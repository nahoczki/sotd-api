package sotd.song;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-model projection for the best common song between two users for one period.
 */
public record OurSongMatchView(
        UUID appUserId,
        UUID otherUserId,
        OurSongPeriodType periodType,
        LocalDate periodStartLocal,
        String spotifyTrackId,
        String trackName,
        int userPlayCount,
        int otherUserPlayCount,
        int combinedPlayCount,
        String tieBreakRule
) {
}
