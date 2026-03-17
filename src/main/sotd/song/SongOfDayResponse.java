package sotd.song;

public record SongOfDayResponse(
        String status,
        String message
) {

    public static SongOfDayResponse unavailable() {
        return new SongOfDayResponse(
                "pending",
                "No song-of-the-day data has been computed yet."
        );
    }
}
