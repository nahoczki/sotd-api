package sotd.song;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import sotd.spotify.persistence.SpotifyAccountRepository;

@Service
public class SongOfDayService {

    private final SongOfDayRepository songOfDayRepository;
    private final SpotifyAccountRepository spotifyAccountRepository;
    private final Clock clock;

    public SongOfDayService(
            SongOfDayRepository songOfDayRepository,
            SpotifyAccountRepository spotifyAccountRepository,
            Clock clock
    ) {
        this.songOfDayRepository = songOfDayRepository;
        this.spotifyAccountRepository = spotifyAccountRepository;
        this.clock = clock;
    }

    public SongOfDayResponse getCurrentSongOfDay(String spotifyUserId) {
        Optional<ResolvedSongOfDayRequest> resolvedRequest = resolveRequest(spotifyUserId);
        if (resolvedRequest.isEmpty()) {
            return SongOfDayResponse.unavailable();
        }

        LocalDate localDate = clock.instant()
                .atZone(ZoneId.of(resolvedRequest.get().timezone()))
                .toLocalDate();

        return songOfDayRepository.findCurrentWinner(resolvedRequest.get().spotifyUserId(), localDate)
                .map(SongOfDayResponse::available)
                .orElseGet(SongOfDayResponse::unavailable);
    }

    private Optional<ResolvedSongOfDayRequest> resolveRequest(String spotifyUserId) {
        if (StringUtils.hasText(spotifyUserId)) {
            return spotifyAccountRepository.findTimezoneBySpotifyUserId(spotifyUserId)
                    .map(timezone -> new ResolvedSongOfDayRequest(spotifyUserId, timezone));
        }

        return spotifyAccountRepository.findMostRecentAccountIdentity()
                .map(account -> new ResolvedSongOfDayRequest(account.spotifyUserId(), account.timezone()));
    }

    private record ResolvedSongOfDayRequest(
            String spotifyUserId,
            String timezone
    ) {
    }
}
