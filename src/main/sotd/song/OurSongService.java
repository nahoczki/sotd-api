package sotd.song;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import sotd.spotify.persistence.SpotifyAccountRepository;
import sotd.spotify.persistence.SpotifyAccountRepository.LinkedSpotifyAccountIdentity;

/**
 * Resolves the highest-ranked shared song between two application users.
 *
 * <p>The comparison window is anchored to the requesting user's timezone and then applied as the same
 * local-date range label to both users' daily rollups.
 */
@Service
public class OurSongService {

    private final OurSongRepository ourSongRepository;
    private final SpotifyAccountRepository spotifyAccountRepository;
    private final Clock clock;

    public OurSongService(
            OurSongRepository ourSongRepository,
            SpotifyAccountRepository spotifyAccountRepository,
            Clock clock
    ) {
        this.ourSongRepository = ourSongRepository;
        this.spotifyAccountRepository = spotifyAccountRepository;
        this.clock = clock;
    }

    public OurSongResponse getCurrentSharedSong(UUID appUserId, UUID otherUserId, OurSongPeriodType periodType) {
        if (appUserId.equals(otherUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Our-song comparison requires two distinct users.");
        }

        Optional<LinkedSpotifyAccountIdentity> linkedAccount = spotifyAccountRepository.findAccountIdentityByAppUserId(appUserId);
        if (linkedAccount.isEmpty()) {
            return OurSongResponse.unlinked(
                    appUserId,
                    otherUserId,
                    periodType,
                    null,
                    "No Spotify account is linked for the requesting user."
            );
        }

        LocalDate anchorDate = clock.instant()
                .atZone(ZoneId.of(linkedAccount.get().timezone()))
                .toLocalDate();
        OurSongPeriodType.PeriodWindow window = periodType.resolveWindow(anchorDate);

        if (spotifyAccountRepository.findAccountIdentityByAppUserId(otherUserId).isEmpty()) {
            return OurSongResponse.unlinked(
                    appUserId,
                    otherUserId,
                    periodType,
                    window.periodStartLocal(),
                    "No Spotify account is linked for the comparison user."
            );
        }

        return ourSongRepository.findBestSharedSong(
                        appUserId,
                        otherUserId,
                        periodType,
                        window.periodStartLocal(),
                        window.periodEndExclusive()
                )
                .map(OurSongResponse::available)
                .orElseGet(() -> OurSongResponse.noCommonSong(
                        appUserId,
                        otherUserId,
                        periodType,
                        window.periodStartLocal()
                ));
    }
}
