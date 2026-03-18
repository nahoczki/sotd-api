package sotd.spotify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import sotd.crypto.TokenEncryptionService;
import sotd.spotify.client.SpotifyAccountsClient;
import sotd.spotify.client.SpotifyTokenResponse;
import sotd.spotify.persistence.SpotifyAccountRepository;
import sotd.spotify.persistence.SpotifyPollingAccount;

/**
 * Reuses cached access tokens when possible and refreshes them on demand from the stored refresh token.
 */
@Service
public class SpotifyAccessTokenService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyAccessTokenService.class);
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

    private final SpotifyProperties spotifyProperties;
    private final SpotifyAccountsClient spotifyAccountsClient;
    private final TokenEncryptionService tokenEncryptionService;
    private final SpotifyAccountRepository spotifyAccountRepository;
    private final Clock clock;
    private final Map<Long, CachedAccessToken> tokenCache = new ConcurrentHashMap<>();

    public SpotifyAccessTokenService(
            SpotifyProperties spotifyProperties,
            SpotifyAccountsClient spotifyAccountsClient,
            TokenEncryptionService tokenEncryptionService,
            SpotifyAccountRepository spotifyAccountRepository,
            Clock clock
    ) {
        this.spotifyProperties = spotifyProperties;
        this.spotifyAccountsClient = spotifyAccountsClient;
        this.tokenEncryptionService = tokenEncryptionService;
        this.spotifyAccountRepository = spotifyAccountRepository;
        this.clock = clock;
    }

    public String getAccessToken(SpotifyPollingAccount account) {
        Instant now = clock.instant();
        CachedAccessToken cachedAccessToken = tokenCache.get(account.id());
        if (cachedAccessToken != null && cachedAccessToken.expiresAt().isAfter(now.plus(EXPIRY_SKEW))) {
            return cachedAccessToken.accessToken();
        }
        return refreshAccessToken(account, now);
    }

    public void invalidate(long accountId) {
        tokenCache.remove(accountId);
    }

    private String refreshAccessToken(SpotifyPollingAccount account, Instant now) {
        requireConfiguredCredentials();
        try {
            String refreshToken = tokenEncryptionService.decrypt(account.refreshTokenEncrypted());
            SpotifyTokenResponse response = spotifyAccountsClient.refreshAccessToken(
                    spotifyProperties.getClientId(),
                    spotifyProperties.getClientSecret(),
                    refreshToken
            );

            Instant expiresAt = now.plusSeconds(response.expiresIn());
            byte[] encryptedRefreshToken = account.refreshTokenEncrypted();
            if (StringUtils.hasText(response.refreshToken())) {
                encryptedRefreshToken = tokenEncryptionService.encrypt(response.refreshToken());
            }

            spotifyAccountRepository.updateTokenState(account.id(), encryptedRefreshToken, expiresAt, now);
            tokenCache.put(account.id(), new CachedAccessToken(response.accessToken(), expiresAt));
            log.debug("Refreshed Spotify access token for account {}. Expires at {}.", account.spotifyUserId(), expiresAt);
            return response.accessToken();
        }
        catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST || ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                tokenCache.remove(account.id());
                spotifyAccountRepository.markReauthRequired(account.id());
                log.warn("Spotify account {} requires reauthorization after refresh-token failure.", account.spotifyUserId());
                throw new SpotifyReauthRequiredException("Spotify account requires reauthorization.", ex);
            }
            throw ex;
        }
    }

    private void requireConfiguredCredentials() {
        if (!StringUtils.hasText(spotifyProperties.getClientId())
                || !StringUtils.hasText(spotifyProperties.getClientSecret())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Spotify client credentials are not configured. Add SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET to .env."
            );
        }
    }

    record CachedAccessToken(
            String accessToken,
            Instant expiresAt
    ) {
    }
}
