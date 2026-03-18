package sotd.spotify;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import sotd.crypto.TokenEncryptionService;
import sotd.spotify.client.SpotifyAccountsClient;
import sotd.spotify.client.SpotifyApiClient;
import sotd.spotify.client.SpotifyCurrentUserProfile;
import sotd.spotify.client.SpotifyTokenResponse;
import sotd.spotify.persistence.SpotifyAccountRepository.DisconnectedSpotifyAccount;
import sotd.spotify.persistence.SpotifyAccountRepository;
import sotd.spotify.SpotifyAccessTokenService;

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
    private final Clock clock;

    public SpotifyAuthorizationService(
            SpotifyProperties spotifyProperties,
            SpotifyAuthStateStore authStateStore,
            SpotifyAccountsClient spotifyAccountsClient,
            SpotifyApiClient spotifyApiClient,
            TokenEncryptionService tokenEncryptionService,
            SpotifyAccountRepository spotifyAccountRepository,
            SpotifyAccessTokenService spotifyAccessTokenService,
            Clock clock
    ) {
        this.spotifyProperties = spotifyProperties;
        this.authStateStore = authStateStore;
        this.spotifyAccountsClient = spotifyAccountsClient;
        this.spotifyApiClient = spotifyApiClient;
        this.tokenEncryptionService = tokenEncryptionService;
        this.spotifyAccountRepository = spotifyAccountRepository;
        this.spotifyAccessTokenService = spotifyAccessTokenService;
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
        requireConfiguredCredentials();

        if (StringUtils.hasText(error)) {
            log.warn("Spotify authorization callback returned an error: {}", error);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Spotify authorization failed: " + error);
        }
        if (!StringUtils.hasText(code)) {
            log.warn("Rejected Spotify callback because the authorization code was missing.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Spotify callback is missing the authorization code.");
        }
        UUID appUserId = authStateStore.consume(state)
                .orElseThrow(() -> {
                    log.warn("Rejected Spotify callback because the state token was invalid or expired.");
                    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Spotify callback state is invalid or expired.");
                });

        SpotifyTokenResponse tokenResponse = spotifyAccountsClient.exchangeAuthorizationCode(
                spotifyProperties.getClientId(),
                spotifyProperties.getClientSecret(),
                code,
                spotifyProperties.getRedirectUri()
        );

        if (!StringUtils.hasText(tokenResponse.refreshToken())) {
            log.warn("Spotify token exchange completed without returning a refresh token.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Spotify did not return a refresh token.");
        }

        SpotifyCurrentUserProfile userProfile = spotifyApiClient.getCurrentUserProfile(tokenResponse.accessToken());
        Instant tokenExpiresAt = clock.instant().plusSeconds(tokenResponse.expiresIn());

        spotifyAccountRepository.saveOrUpdate(
                appUserId,
                userProfile,
                tokenEncryptionService.encrypt(tokenResponse.refreshToken()),
                tokenResponse.scope(),
                tokenExpiresAt,
                clock.instant(),
                ZoneId.systemDefault().getId(),
                clock.instant().toEpochMilli()
        );
        log.info(
                "Linked Spotify account {} ({}) to app user {} with token expiry at {}.",
                userProfile.id(),
                userProfile.displayName(),
                appUserId,
                tokenExpiresAt
        );

        return new SpotifyConnectionResponse(
                "connected",
                appUserId,
                userProfile.id(),
                userProfile.displayName(),
                tokenResponse.scope(),
                tokenExpiresAt
        );
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
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Spotify client credentials are not configured. Add SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET to .env."
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
