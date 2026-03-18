package sotd.song;

import java.time.LocalDate;

/**
 * Read-model projection for a computed song-of-the-day winner.
 */
public record SongOfDayWinnerView(
        String spotifyUserId,
        String displayName,
        String timezone,
        LocalDate periodStartLocal,
        String spotifyTrackId,
        String trackName,
        int playCount
) {
}
