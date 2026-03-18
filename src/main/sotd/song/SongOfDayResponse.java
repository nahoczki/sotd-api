package sotd.song;

import java.time.LocalDate;

public record SongOfDayResponse(
        String status,
        String message,
        String spotifyUserId,
        String displayName,
        LocalDate periodStartLocal,
        String spotifyTrackId,
        String trackName,
        Integer playCount
) {

    public static SongOfDayResponse unavailable() {
        return new SongOfDayResponse(
                "pending",
                "No song-of-the-day data has been computed yet.",
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static SongOfDayResponse available(SongOfDayWinnerView winner) {
        return new SongOfDayResponse(
                "ready",
                "Song-of-the-day data is available.",
                winner.spotifyUserId(),
                winner.displayName(),
                winner.periodStartLocal(),
                winner.spotifyTrackId(),
                winner.trackName(),
                winner.playCount()
        );
    }
}
