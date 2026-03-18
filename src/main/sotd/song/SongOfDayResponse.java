package sotd.song;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Song-of-the-day result for a specific application user.")
public record SongOfDayResponse(
        @Schema(description = "High-level response state.", allowableValues = {"ready", "pending", "unlinked"})
        String status,
        @Schema(description = "Human-readable explanation of the current state.")
        String message,
        @Schema(description = "Stable upstream application user UUID.")
        UUID appUserId,
        @Schema(description = "Linked Spotify user id when available.")
        String spotifyUserId,
        @Schema(description = "Spotify display name when available.")
        String displayName,
        @Schema(description = "Local day used for the winner calculation.")
        LocalDate periodStartLocal,
        @Schema(description = "Winning Spotify track id when a winner exists.")
        String spotifyTrackId,
        @Schema(description = "Winning track name when a winner exists.")
        String trackName,
        @Schema(description = "Winning play count for the selected day.")
        Integer playCount
) {

    public static SongOfDayResponse unlinked(UUID appUserId) {
        return new SongOfDayResponse(
                "unlinked",
                "No Spotify account is linked for this user.",
                appUserId,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static SongOfDayResponse pending(UUID appUserId) {
        return new SongOfDayResponse(
                "pending",
                "No song-of-the-day data has been computed yet.",
                appUserId,
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
                winner.appUserId(),
                winner.spotifyUserId(),
                winner.displayName(),
                winner.periodStartLocal(),
                winner.spotifyTrackId(),
                winner.trackName(),
                winner.playCount()
        );
    }
}
