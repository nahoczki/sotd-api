package sotd.spotify;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the first recently-played ingestion worker.
 */
@Component
public class SpotifyPlaybackPollingJob {

    private final SpotifyPlaybackPollingService spotifyPlaybackPollingService;

    public SpotifyPlaybackPollingJob(SpotifyPlaybackPollingService spotifyPlaybackPollingService) {
        this.spotifyPlaybackPollingService = spotifyPlaybackPollingService;
    }

    @Scheduled(
            initialDelayString = "#{T(java.time.Duration).parse('${spotify.polling.recently-played-interval:PT2M}').toMillis()}",
            fixedDelayString = "#{T(java.time.Duration).parse('${spotify.polling.recently-played-interval:PT2M}').toMillis()}"
    )
    public void pollRecentlyPlayed() {
        spotifyPlaybackPollingService.pollActiveAccounts();
    }
}
