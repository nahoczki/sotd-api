package sotd.spotify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import sotd.song.SongRollupRepository;
import sotd.spotify.client.SpotifyApiClient;
import sotd.spotify.client.SpotifyRecentlyPlayedResponse;
import sotd.spotify.persistence.SpotifyAccountRepository;
import sotd.spotify.persistence.SpotifyPollingAccount;

/**
 * Background orchestration for polling Spotify recently-played history and storing it locally.
 */
@Service
public class SpotifyPlaybackPollingService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyPlaybackPollingService.class);
    private static final int RECENTLY_PLAYED_LIMIT = 50;

    private final SpotifyAccountRepository spotifyAccountRepository;
    private final SpotifyAccessTokenService spotifyAccessTokenService;
    private final SpotifyApiClient spotifyApiClient;
    private final SpotifyRecentlyPlayedIngestionService ingestionService;
    private final SongRollupRepository songRollupRepository;
    private final Clock clock;

    public SpotifyPlaybackPollingService(
            SpotifyAccountRepository spotifyAccountRepository,
            SpotifyAccessTokenService spotifyAccessTokenService,
            SpotifyApiClient spotifyApiClient,
            SpotifyRecentlyPlayedIngestionService ingestionService,
            SongRollupRepository songRollupRepository,
            Clock clock
    ) {
        this.spotifyAccountRepository = spotifyAccountRepository;
        this.spotifyAccessTokenService = spotifyAccessTokenService;
        this.spotifyApiClient = spotifyApiClient;
        this.ingestionService = ingestionService;
        this.songRollupRepository = songRollupRepository;
        this.clock = clock;
    }

    public void pollActiveAccounts() {
        List<SpotifyPollingAccount> accounts = spotifyAccountRepository.findActiveAccountsForPolling();
        if (accounts.isEmpty()) {
            return;
        }

        for (SpotifyPollingAccount account : accounts) {
            pollAccount(account);
        }
    }

    void pollAccount(SpotifyPollingAccount account) {
        if (!spotifyAccountRepository.isActiveForPolling(account.id())) {
            return;
        }

        try {
            SpotifyRecentlyPlayedResponse response = fetchRecentlyPlayed(account);
            if (!spotifyAccountRepository.isActiveForPolling(account.id())) {
                return;
            }
            SpotifyRecentlyPlayedIngestionService.RecentlyPlayedIngestionResult result = ingestionService.ingest(account, response);

            for (LocalDate affectedDay : result.affectedDays()) {
                songRollupRepository.rebuildDay(account.id(), affectedDay);
            }

            Instant now = clock.instant();
            spotifyAccountRepository.updatePollingCheckpoint(account.id(), now, result.newestCursorMs());
            log.info(
                    "Polled Spotify account {}. Inserted {} new playback events.",
                    account.spotifyUserId(),
                    result.insertedEvents()
            );
        }
        catch (SpotifyReauthRequiredException ex) {
            log.warn("Skipping polling for Spotify account {} until it is reauthorized.", account.spotifyUserId());
        }
        catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Spotify rate-limited recently-played polling for account {}.", account.spotifyUserId());
                return;
            }
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                spotifyAccessTokenService.invalidate(account.id());
                spotifyAccountRepository.markReauthRequired(account.id());
                log.warn("Spotify account {} was unauthorized during recently-played polling.", account.spotifyUserId());
                return;
            }
            throw ex;
        }
    }

    private SpotifyRecentlyPlayedResponse fetchRecentlyPlayed(SpotifyPollingAccount account) {
        String accessToken = spotifyAccessTokenService.getAccessToken(account);
        try {
            return spotifyApiClient.getRecentlyPlayed(accessToken, account.lastRecentlyPlayedCursorMs(), RECENTLY_PLAYED_LIMIT);
        }
        catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                spotifyAccessTokenService.invalidate(account.id());
                String refreshedAccessToken = spotifyAccessTokenService.getAccessToken(account);
                return spotifyApiClient.getRecentlyPlayed(
                        refreshedAccessToken,
                        account.lastRecentlyPlayedCursorMs(),
                        RECENTLY_PLAYED_LIMIT
                );
            }
            throw ex;
        }
    }
}
