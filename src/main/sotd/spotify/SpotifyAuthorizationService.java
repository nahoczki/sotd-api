package sotd.spotify;

import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import sotd.crypto.TokenEncryptionService;
import sotd.spotify.client.SpotifyAccountsClient;
import sotd.spotify.client.SpotifyApiClient;
import sotd.spotify.client.SpotifyCurrentUserProfile;
import sotd.spotify.client.SpotifyTokenResponse;
import sotd.spotify.persistence.SpotifyAccountRepository.DisconnectedSpotifyAccount;
import sotd.spotify.persistence.SpotifyAccountRepository;

@Service
/**
 * Coordinates the Spotify OAuth flow for user-scoped Spotify account linking.
 *
 * <p>The `app_user_id` comes from an upstream system. This service does not create or validate those
 * users locally; it only binds a successful Spotify OAuth callback to the UUID carried through the
 * one-time state token.
 */
public class SpotifyAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyAuthorizationService.class);

    private final SpotifyProperties spotifyProperties;
    private final SpotifyAuthStateStore authStateStore;
    private final SpotifyAccountsClient spotifyAccountsClient;
    private final SpotifyApiClient spotifyApiClient;
    private final TokenEncryptionService tokenEncryptionService;
    private final SpotifyAccountRepository spotifyAccountRepository;
    private final SpotifyAccessTokenService spotifyAccessTokenService;
    private final SpotifyOperationalMetrics spotifyOperationalMetrics;
    private final Clock clock;

    public SpotifyAuthorizationService(
            SpotifyProperties spotifyProperties,
            SpotifyAuthStateStore authStateStore,
            SpotifyAccountsClient spotifyAccountsClient,
            SpotifyApiClient spotifyApiClient,
            TokenEncryptionService tokenEncryptionService,
            SpotifyAccountRepository spotifyAccountRepository,
            SpotifyAccessTokenService spotifyAccessTokenService,
            SpotifyOperationalMetrics spotifyOperationalMetrics,
            Clock clock
    ) {
        this.spotifyProperties = spotifyProperties;
        this.authStateStore = authStateStore;
        this.spotifyAccountsClient = spotifyAccountsClient;
        this.spotifyApiClient = spotifyApiClient;
        this.tokenEncryptionService = tokenEncryptionService;
        this.spotifyAccountRepository = spotifyAccountRepository;
        this.spotifyAccessTokenService = spotifyAccessTokenService;
        this.spotifyOperationalMetrics = spotifyOperationalMetrics;
        this.clock = clock;
    }

    public URI buildAuthorizationUri(UUID appUserId) {
        requireConfiguredCredentials();

        Instant now = clock.instant();
        Instant expiresAt = now.plus(spotifyProperties.getAuthStateTtl());
        String state = authStateStore.issueState(appUserId, expiresAt);
        log.debug("Issued Spotify authorization state expiring at {}", expiresAt);

        return UriComponentsBuilder.fromUri(spotifyProperties.getAccountsBaseUrl())
                .path("/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", spotifyProperties.getClientId())
                .queryParam("redirect_uri", spotifyProperties.getRedirectUri())
                .queryParam("scope", joinScopes(spotifyProperties.getScopes()))
                .queryParam("state", state)
                .queryParam("show_dialog", spotifyProperties.isShowDialog())
                .build()
                .encode()
                .toUri();
    }

    public SpotifyConnectionResponse handleCallback(String code, String state, String error) {
        Timer.Sample callbackSample = spotifyOperationalMetrics.startCallbackTimer();
        try {
            try {
                requireConfiguredCredentials();
            }
            catch (ResponseStatusException ex) {
                log.error("Spotify callback failed during {} because credentials are not configured.", SpotifyCallbackStage.CONFIGURATION);
                throw SpotifyCallbackException.configuration(
                        "Spotify client credentials are not configured.",
                        ex
                );
            }

            UUID appUserId = consumeStateOrThrow(state);

            if (StringUtils.hasText(error)) {
                log.warn(
                        "Spotify callback failed during {} for app user {}: spotify returned error '{}'.",
                        SpotifyCallbackStage.AUTHORIZATION_DENIED,
                        appUserId,
                        error
                );
                throw SpotifyCallbackException.authorizationDenied(appUserId, "Spotify authorization was denied or cancelled.");
            }
            if (!StringUtils.hasText(code)) {
                log.warn(
                        "Spotify callback failed during {} for app user {}: authorization code was missing.",
                        SpotifyCallbackStage.MISSING_CODE,
                        appUserId
                );
                throw SpotifyCallbackException.missingCode(appUserId, "Spotify callback is missing the authorization code.");
            }

            SpotifyTokenResponse tokenResponse = exchangeAuthorizationCode(appUserId, code);
            SpotifyCurrentUserProfile userProfile = loadCurrentUserProfile(appUserId, tokenResponse.accessToken());
            Instant now = clock.instant();
            Instant tokenExpiresAt = now.plusSeconds(tokenResponse.expiresIn());

            persistLinkedAccount(appUserId, userProfile, tokenResponse, now, tokenExpiresAt);
            log.info(
                    "Linked Spotify account {} ({}) to app user {} with token expiry at {}.",
                    userProfile.id(),
                    userProfile.displayName(),
                    appUserId,
                    tokenExpiresAt
            );

            SpotifyConnectionResponse response = new SpotifyConnectionResponse(
                    "connected",
                    appUserId,
                    userProfile.id(),
                    userProfile.displayName(),
                    tokenResponse.scope(),
                    tokenExpiresAt
            );
            spotifyOperationalMetrics.recordCallbackSuccess(callbackSample);
            return response;
        }
        catch (SpotifyCallbackException ex) {
            spotifyOperationalMetrics.recordCallbackFailure(ex.getStage(), callbackSample);
            throw ex;
        }
    }

    public Optional<SpotifyLinkedAccountView> getCurrentConnection(UUID appUserId) {
        return spotifyAccountRepository.findByAppUserId(appUserId);
    }

    public void disconnectCurrentConnection(UUID appUserId) {
        Optional<DisconnectedSpotifyAccount> disconnectedAccount = spotifyAccountRepository.disconnectByAppUserId(appUserId);
        disconnectedAccount.ifPresent(account -> {
            spotifyAccessTokenService.invalidate(account.accountId());
            log.info("Disconnected Spotify account {} from app user {}.", account.spotifyUserId(), appUserId);
        });
    }

    private void requireConfiguredCredentials() {
        if (!StringUtils.hasText(spotifyProperties.getClientId())
                || !StringUtils.hasText(spotifyProperties.getClientSecret())) {
            log.error("Spotify client credentials are not configured.");
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Spotify client credentials are not configured. Add SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET to .env."
            );
        }
    }

    private UUID consumeStateOrThrow(String state) {
        UUID appUserId = authStateStore.consume(state)
                .orElseThrow(() -> {
                    log.warn(
                            "Spotify callback failed during {} because the state token was invalid or expired.",
                            SpotifyCallbackStage.STATE_VALIDATION
                    );
                    return SpotifyCallbackException.invalidState("Spotify callback state is invalid or expired.");
                });
        log.debug("Validated Spotify callback state for app user {}.", appUserId);
        return appUserId;
    }

    private SpotifyTokenResponse exchangeAuthorizationCode(UUID appUserId, String code) {
        try {
            SpotifyTokenResponse tokenResponse = spotifyAccountsClient.exchangeAuthorizationCode(
                    spotifyProperties.getClientId(),
                    spotifyProperties.getClientSecret(),
                    code,
                    spotifyProperties.getRedirectUri()
            );

            if (!StringUtils.hasText(tokenResponse.refreshToken())) {
                log.warn(
                        "Spotify callback failed during {} for app user {}: token exchange returned no refresh token.",
                        SpotifyCallbackStage.TOKEN_EXCHANGE,
                        appUserId
                );
                throw SpotifyCallbackException.tokenExchange(
                        appUserId,
                        "Spotify did not return a refresh token.",
                        null
                );
            }

            return tokenResponse;
        }
        catch (RestClientResponseException ex) {
            log.error(
                    "Spotify callback failed during {} for app user {}: Spotify Accounts API returned status {}.",
                    SpotifyCallbackStage.TOKEN_EXCHANGE,
                    appUserId,
                    ex.getStatusCode(),
                    ex
            );
            throw SpotifyCallbackException.tokenExchange(
                    appUserId,
                    "Spotify token exchange failed.",
                    ex
            );
        }
        catch (ResponseStatusException ex) {
            log.error(
                    "Spotify callback failed during {} for app user {}: token exchange produced an invalid response.",
                    SpotifyCallbackStage.TOKEN_EXCHANGE,
                    appUserId,
                    ex
            );
            throw SpotifyCallbackException.tokenExchange(
                    appUserId,
                    ex.getReason() != null ? ex.getReason() : "Spotify token exchange failed.",
                    ex
            );
        }
    }

    private SpotifyCurrentUserProfile loadCurrentUserProfile(UUID appUserId, String accessToken) {
        try {
            return spotifyApiClient.getCurrentUserProfile(accessToken);
        }
        catch (RestClientResponseException ex) {
            log.error(
                    "Spotify callback failed during {} for app user {}: Spotify /me returned status {}.",
                    SpotifyCallbackStage.PROFILE_LOOKUP,
                    appUserId,
                    ex.getStatusCode(),
                    ex
            );
            throw SpotifyCallbackException.profileLookup(
                    appUserId,
                    "Spotify profile lookup failed.",
                    ex
            );
        }
        catch (ResponseStatusException ex) {
            log.error(
                    "Spotify callback failed during {} for app user {}: profile lookup returned an invalid response.",
                    SpotifyCallbackStage.PROFILE_LOOKUP,
                    appUserId,
                    ex
            );
            throw SpotifyCallbackException.profileLookup(
                    appUserId,
                    ex.getReason() != null ? ex.getReason() : "Spotify profile lookup failed.",
                    ex
            );
        }
    }

    private void persistLinkedAccount(
            UUID appUserId,
            SpotifyCurrentUserProfile userProfile,
            SpotifyTokenResponse tokenResponse,
            Instant now,
            Instant tokenExpiresAt
    ) {
        try {
            spotifyAccountRepository.saveOrUpdate(
                    appUserId,
                    userProfile,
                    tokenEncryptionService.encrypt(tokenResponse.refreshToken()),
                    tokenResponse.scope(),
                    tokenExpiresAt,
                    now,
                    ZoneId.systemDefault().getId(),
                    now.toEpochMilli()
            );
        }
        catch (RuntimeException ex) {
            log.error(
                    "Spotify callback failed during {} for app user {} while persisting Spotify account {}.",
                    SpotifyCallbackStage.PERSISTENCE,
                    appUserId,
                    userProfile.id(),
                    ex
            );
            throw SpotifyCallbackException.persistence(
                    appUserId,
                    "Spotify account link could not be saved.",
                    ex
            );
        }
    }

    private static String joinScopes(List<String> scopes) {
        StringJoiner joiner = new StringJoiner(" ");
        scopes.stream()
                .filter(StringUtils::hasText)
                .forEach(joiner::add);
        return joiner.toString();
    }
}
