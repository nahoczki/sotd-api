package sotd.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import sotd.crypto.CryptoProperties;
import sotd.crypto.TokenEncryptionService;
import sotd.spotify.client.SpotifyAccountsClient;
import sotd.spotify.client.SpotifyApiClient;
import sotd.spotify.client.SpotifyCurrentUserProfile;
import sotd.spotify.client.SpotifyTokenResponse;
import sotd.spotify.persistence.SpotifyAccountRepository;

class SpotifyAuthorizationServiceTest {

    @Test
    void buildAuthorizationUriIncludesExpectedSpotifyParameters() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-17T20:00:00Z"), ZoneOffset.UTC);
        SpotifyProperties properties = configuredProperties();
        SpotifyAuthStateStore stateStore = mock(SpotifyAuthStateStore.class);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(stateStore.issueState(appUserId, Instant.parse("2026-03-17T20:10:00Z"))).thenReturn("state-123");

        SpotifyAuthorizationService service = new SpotifyAuthorizationService(
                properties,
                stateStore,
                mock(SpotifyAccountsClient.class),
                mock(SpotifyApiClient.class),
                encryptionService(),
                mock(SpotifyAccountRepository.class),
                mock(SpotifyAccessTokenService.class),
                mock(SpotifyOperationalMetrics.class),
                clock
        );

        URI authorizationUri = service.buildAuthorizationUri(appUserId);

        assertThat(authorizationUri.toString())
                .isEqualTo("https://accounts.spotify.test/authorize?response_type=code&client_id=client-id&redirect_uri=http://127.0.0.1:8080/api/spotify/callback&scope=user-read-private%20user-read-recently-played&state=state-123&show_dialog=true");
    }

    @Test
    void handleCallbackExchangesCodePersistsAccountAndReturnsConnectionSummary() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-17T20:00:00Z"), ZoneOffset.UTC);
        SpotifyProperties properties = configuredProperties();
        SpotifyAuthStateStore stateStore = mock(SpotifyAuthStateStore.class);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(stateStore.consume("state-123")).thenReturn(Optional.of(appUserId));

        SpotifyAccountsClient accountsClient = mock(SpotifyAccountsClient.class);
        when(accountsClient.exchangeAuthorizationCode(anyString(), anyString(), anyString(), any(URI.class)))
                .thenReturn(new SpotifyTokenResponse(
                        "access-token",
                        "Bearer",
                        "user-read-private user-read-recently-played",
                        3600,
                        "refresh-token"
                ));

        SpotifyApiClient apiClient = mock(SpotifyApiClient.class);
        when(apiClient.getCurrentUserProfile("access-token"))
                .thenReturn(new SpotifyCurrentUserProfile("spotify-user", "Luke"));

        SpotifyAccountRepository repository = mock(SpotifyAccountRepository.class);

        SpotifyAuthorizationService service = new SpotifyAuthorizationService(
                properties,
                stateStore,
                accountsClient,
                apiClient,
                encryptionService(),
                repository,
                mock(SpotifyAccessTokenService.class),
                mock(SpotifyOperationalMetrics.class),
                clock
        );

        SpotifyConnectionResponse response = service.handleCallback("code-123", "state-123", null);

        assertThat(response.status()).isEqualTo("connected");
        assertThat(response.appUserId()).isEqualTo(appUserId);
        assertThat(response.spotifyUserId()).isEqualTo("spotify-user");
        assertThat(response.displayName()).isEqualTo("Luke");
        assertThat(response.grantedScope()).isEqualTo("user-read-private user-read-recently-played");
        assertThat(response.accessTokenExpiresAt()).isEqualTo(Instant.parse("2026-03-17T21:00:00Z"));

        verify(repository).saveOrUpdate(
                eq(appUserId),
                any(SpotifyCurrentUserProfile.class),
                any(byte[].class),
                anyString(),
                any(Instant.class),
                any(Instant.class),
                anyString(),
                anyLong()
        );
    }

    @Test
    void handleCallbackRejectsInvalidState() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-17T20:00:00Z"), ZoneOffset.UTC);
        SpotifyProperties properties = configuredProperties();
        SpotifyAuthStateStore stateStore = mock(SpotifyAuthStateStore.class);
        when(stateStore.consume("bad-state")).thenReturn(Optional.empty());

        SpotifyAuthorizationService service = new SpotifyAuthorizationService(
                properties,
                stateStore,
                mock(SpotifyAccountsClient.class),
                mock(SpotifyApiClient.class),
                encryptionService(),
                mock(SpotifyAccountRepository.class),
                mock(SpotifyAccessTokenService.class),
                mock(SpotifyOperationalMetrics.class),
                clock
        );

        assertThatThrownBy(() -> service.handleCallback("code-123", "bad-state", null))
                .isInstanceOf(SpotifyCallbackException.class)
                .satisfies(ex -> {
                    SpotifyCallbackException callbackException = (SpotifyCallbackException) ex;
                    assertThat(callbackException.getStage()).isEqualTo(SpotifyCallbackStage.STATE_VALIDATION);
                    assertThat(callbackException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void handleCallbackReturnsAuthorizationDeniedWhenSpotifyReturnsError() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-17T20:00:00Z"), ZoneOffset.UTC);
        SpotifyProperties properties = configuredProperties();
        SpotifyAuthStateStore stateStore = mock(SpotifyAuthStateStore.class);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(stateStore.consume("state-123")).thenReturn(Optional.of(appUserId));

        SpotifyAuthorizationService service = new SpotifyAuthorizationService(
                properties,
                stateStore,
                mock(SpotifyAccountsClient.class),
                mock(SpotifyApiClient.class),
                encryptionService(),
                mock(SpotifyAccountRepository.class),
                mock(SpotifyAccessTokenService.class),
                mock(SpotifyOperationalMetrics.class),
                clock
        );

        assertThatThrownBy(() -> service.handleCallback(null, "state-123", "access_denied"))
                .isInstanceOf(SpotifyCallbackException.class)
                .satisfies(ex -> {
                    SpotifyCallbackException callbackException = (SpotifyCallbackException) ex;
                    assertThat(callbackException.getStage()).isEqualTo(SpotifyCallbackStage.AUTHORIZATION_DENIED);
                    assertThat(callbackException.getAppUserId()).isEqualTo(appUserId);
                });
    }

    @Test
    void handleCallbackSurfacesTokenExchangeFailureAsStructuredCallbackError() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-17T20:00:00Z"), ZoneOffset.UTC);
        SpotifyProperties properties = configuredProperties();
        SpotifyAuthStateStore stateStore = mock(SpotifyAuthStateStore.class);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(stateStore.consume("state-123")).thenReturn(Optional.of(appUserId));

        SpotifyAccountsClient accountsClient = mock(SpotifyAccountsClient.class);
        when(accountsClient.exchangeAuthorizationCode(anyString(), anyString(), anyString(), any(URI.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "bad gateway", null, null, null));

        SpotifyAuthorizationService service = new SpotifyAuthorizationService(
                properties,
                stateStore,
                accountsClient,
                mock(SpotifyApiClient.class),
                encryptionService(),
                mock(SpotifyAccountRepository.class),
                mock(SpotifyAccessTokenService.class),
                mock(SpotifyOperationalMetrics.class),
                clock
        );

        assertThatThrownBy(() -> service.handleCallback("code-123", "state-123", null))
                .isInstanceOf(SpotifyCallbackException.class)
                .satisfies(ex -> {
                    SpotifyCallbackException callbackException = (SpotifyCallbackException) ex;
                    assertThat(callbackException.getStage()).isEqualTo(SpotifyCallbackStage.TOKEN_EXCHANGE);
                    assertThat(callbackException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(callbackException.getAppUserId()).isEqualTo(appUserId);
                });
    }

    @Test
    void disconnectCurrentConnectionSoftDisconnectsLinkedAccountAndInvalidatesCachedToken() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-17T20:00:00Z"), ZoneOffset.UTC);
        SpotifyAccountRepository repository = mock(SpotifyAccountRepository.class);
        SpotifyAccessTokenService accessTokenService = mock(SpotifyAccessTokenService.class);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(repository.disconnectByAppUserId(appUserId))
                .thenReturn(Optional.of(new SpotifyAccountRepository.DisconnectedSpotifyAccount(7L, "spotify-user")));

        SpotifyAuthorizationService service = new SpotifyAuthorizationService(
                configuredProperties(),
                mock(SpotifyAuthStateStore.class),
                mock(SpotifyAccountsClient.class),
                mock(SpotifyApiClient.class),
                encryptionService(),
                repository,
                accessTokenService,
                mock(SpotifyOperationalMetrics.class),
                clock
        );

        service.disconnectCurrentConnection(appUserId);

        verify(repository).disconnectByAppUserId(appUserId);
        verify(accessTokenService).invalidate(7L);
    }

    private static SpotifyProperties configuredProperties() {
        SpotifyProperties properties = new SpotifyProperties();
        properties.setAccountsBaseUrl(URI.create("https://accounts.spotify.test"));
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri(URI.create("http://127.0.0.1:8080/api/spotify/callback"));
        properties.setScopes(List.of("user-read-private", "user-read-recently-played"));
        properties.setShowDialog(true);
        properties.setAuthStateTtl(Duration.ofMinutes(10));
        return properties;
    }

    private static TokenEncryptionService encryptionService() {
        CryptoProperties cryptoProperties = new CryptoProperties();
        cryptoProperties.setBase64Key("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        return new TokenEncryptionService(cryptoProperties);
    }
}
