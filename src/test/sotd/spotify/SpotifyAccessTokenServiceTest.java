package sotd.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import sotd.crypto.TokenEncryptionService;
import sotd.spotify.client.SpotifyAccountsClient;
import sotd.spotify.client.SpotifyTokenResponse;
import sotd.spotify.persistence.SpotifyAccountRepository;
import sotd.spotify.persistence.SpotifyPollingAccount;

class SpotifyAccessTokenServiceTest {

    @Test
    void getAccessTokenRefreshesAndCachesToken() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC);
        SpotifyProperties properties = configuredProperties();
        SpotifyAccountsClient accountsClient = mock(SpotifyAccountsClient.class);
        when(accountsClient.refreshAccessToken("client-id", "client-secret", "refresh-token"))
                .thenReturn(new SpotifyTokenResponse("access-token", "Bearer", "scope", 3600, null));

        TokenEncryptionService tokenEncryptionService = mock(TokenEncryptionService.class);
        when(tokenEncryptionService.decrypt(any(byte[].class))).thenReturn("refresh-token");

        SpotifyAccountRepository repository = mock(SpotifyAccountRepository.class);
        SpotifyAccessTokenService service = new SpotifyAccessTokenService(
                properties,
                accountsClient,
                tokenEncryptionService,
                repository,
                clock
        );

        SpotifyPollingAccount account = account();

        assertThat(service.getAccessToken(account)).isEqualTo("access-token");
        assertThat(service.getAccessToken(account)).isEqualTo("access-token");

        verify(accountsClient, times(1)).refreshAccessToken("client-id", "client-secret", "refresh-token");
        verify(repository).updateTokenState(
                eq(account.id()),
                eq(account.refreshTokenEncrypted()),
                eq(Instant.parse("2026-03-18T01:00:00Z")),
                eq(Instant.parse("2026-03-18T00:00:00Z"))
        );
    }

    @Test
    void getAccessTokenMarksAccountForReauthWhenRefreshFailsUnauthorized() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC);
        SpotifyProperties properties = configuredProperties();
        SpotifyAccountsClient accountsClient = mock(SpotifyAccountsClient.class);
        when(accountsClient.refreshAccessToken("client-id", "client-secret", "refresh-token"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        null
                ));

        TokenEncryptionService tokenEncryptionService = mock(TokenEncryptionService.class);
        when(tokenEncryptionService.decrypt(any(byte[].class))).thenReturn("refresh-token");

        SpotifyAccountRepository repository = mock(SpotifyAccountRepository.class);
        SpotifyAccessTokenService service = new SpotifyAccessTokenService(
                properties,
                accountsClient,
                tokenEncryptionService,
                repository,
                clock
        );

        assertThatThrownBy(() -> service.getAccessToken(account()))
                .isInstanceOf(SpotifyReauthRequiredException.class)
                .hasMessageContaining("reauthorization");

        verify(repository).markReauthRequired(anyLong());
    }

    private static SpotifyProperties configuredProperties() {
        SpotifyProperties properties = new SpotifyProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        return properties;
    }

    private static SpotifyPollingAccount account() {
        return new SpotifyPollingAccount(
                7L,
                "spotify-user",
                new byte[] {1, 2, 3},
                null,
                100L,
                "UTC"
        );
    }
}
