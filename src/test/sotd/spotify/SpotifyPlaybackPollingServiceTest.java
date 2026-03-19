package sotd.spotify;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import sotd.song.SongRollupRepository;
import sotd.spotify.client.SpotifyApiClient;
import sotd.spotify.client.SpotifyRecentlyPlayedResponse;
import sotd.spotify.persistence.SpotifyAccountRepository;
import sotd.spotify.persistence.SpotifyPollingAccount;

class SpotifyPlaybackPollingServiceTest {

    @Test
    void pollActiveAccountsIngestsPlaybackAndRebuildsDailyRollups() {
        SpotifyPollingAccount account = account();
        SpotifyAccountRepository repository = mock(SpotifyAccountRepository.class);
        when(repository.findActiveAccountsForPolling()).thenReturn(List.of(account));
        when(repository.isActiveForPolling(account.id())).thenReturn(true);

        SpotifyAccessTokenService accessTokenService = mock(SpotifyAccessTokenService.class);
        when(accessTokenService.getAccessToken(account)).thenReturn("access-token");

        SpotifyRecentlyPlayedResponse response = new SpotifyRecentlyPlayedResponse(List.of(), null);
        SpotifyApiClient apiClient = mock(SpotifyApiClient.class);
        when(apiClient.getRecentlyPlayed("access-token", 100L, 50)).thenReturn(response);

        SpotifyRecentlyPlayedIngestionService ingestionService = mock(SpotifyRecentlyPlayedIngestionService.class);
        when(ingestionService.ingest(account, response))
                .thenReturn(new SpotifyRecentlyPlayedIngestionService.RecentlyPlayedIngestionResult(
                        2,
                        250L,
                        Set.of(LocalDate.parse("2026-03-17"))
                ));

        SongRollupRepository rollupRepository = mock(SongRollupRepository.class);

        SpotifyPlaybackPollingService service = new SpotifyPlaybackPollingService(
                repository,
                accessTokenService,
                apiClient,
                ingestionService,
                rollupRepository,
                mock(SpotifyOperationalMetrics.class),
                Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC)
        );

        service.pollActiveAccounts();

        verify(rollupRepository).rebuildDay(7L, LocalDate.parse("2026-03-17"));
        verify(repository).updatePollingCheckpoint(7L, Instant.parse("2026-03-18T00:00:00Z"), 250L);
    }

    @Test
    void pollAccountRetriesOnceWhenSpotifyRejectsCachedAccessToken() {
        SpotifyPollingAccount account = account();
        SpotifyAccountRepository repository = mock(SpotifyAccountRepository.class);
        when(repository.isActiveForPolling(account.id())).thenReturn(true);
        SpotifyAccessTokenService accessTokenService = mock(SpotifyAccessTokenService.class);
        when(accessTokenService.getAccessToken(account)).thenReturn("stale-token", "fresh-token");

        SpotifyApiClient apiClient = mock(SpotifyApiClient.class);
        when(apiClient.getRecentlyPlayed("stale-token", 100L, 50))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        null
                ));

        SpotifyRecentlyPlayedResponse response = new SpotifyRecentlyPlayedResponse(List.of(), null);
        when(apiClient.getRecentlyPlayed("fresh-token", 100L, 50)).thenReturn(response);

        SpotifyRecentlyPlayedIngestionService ingestionService = mock(SpotifyRecentlyPlayedIngestionService.class);
        when(ingestionService.ingest(account, response))
                .thenReturn(new SpotifyRecentlyPlayedIngestionService.RecentlyPlayedIngestionResult(
                        0,
                        100L,
                        Set.of()
                ));

        SpotifyPlaybackPollingService service = new SpotifyPlaybackPollingService(
                repository,
                accessTokenService,
                apiClient,
                ingestionService,
                mock(SongRollupRepository.class),
                mock(SpotifyOperationalMetrics.class),
                Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC)
        );

        service.pollAccount(account);

        verify(accessTokenService).invalidate(7L);
        verify(apiClient).getRecentlyPlayed("fresh-token", 100L, 50);
        verify(repository).updatePollingCheckpoint(7L, Instant.parse("2026-03-18T00:00:00Z"), 100L);
        verify(accessTokenService, times(2)).getAccessToken(eq(account));
    }

    @Test
    void pollAccountSkipsWhenAccountWasDisconnectedAfterFetch() {
        SpotifyPollingAccount account = account();
        SpotifyAccountRepository repository = mock(SpotifyAccountRepository.class);
        when(repository.isActiveForPolling(account.id())).thenReturn(true, false);

        SpotifyAccessTokenService accessTokenService = mock(SpotifyAccessTokenService.class);
        when(accessTokenService.getAccessToken(account)).thenReturn("access-token");

        SpotifyRecentlyPlayedResponse response = new SpotifyRecentlyPlayedResponse(List.of(), null);
        SpotifyApiClient apiClient = mock(SpotifyApiClient.class);
        when(apiClient.getRecentlyPlayed("access-token", 100L, 50)).thenReturn(response);

        SpotifyRecentlyPlayedIngestionService ingestionService = mock(SpotifyRecentlyPlayedIngestionService.class);

        SpotifyPlaybackPollingService service = new SpotifyPlaybackPollingService(
                repository,
                accessTokenService,
                apiClient,
                ingestionService,
                mock(SongRollupRepository.class),
                mock(SpotifyOperationalMetrics.class),
                Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC)
        );

        service.pollAccount(account);

        verify(ingestionService, times(0)).ingest(account, response);
        verify(repository, times(0)).updatePollingCheckpoint(anyLong(), any(Instant.class), any());
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
