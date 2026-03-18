package sotd.spotify;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class SpotifyPlaybackPollingJobTest {

    @Test
    void pollRecentlyPlayedDelegatesToPollingService() {
        SpotifyPlaybackPollingService pollingService = mock(SpotifyPlaybackPollingService.class);
        SpotifyPlaybackPollingJob job = new SpotifyPlaybackPollingJob(pollingService);

        job.pollRecentlyPlayed();

        verify(pollingService).pollActiveAccounts();
    }
}
