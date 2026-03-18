package sotd.song;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Shared-song result for two application users.")
public record OurSongResponse(
        @Schema(description = "High-level response state.", allowableValues = {"ready", "no-common-song", "unlinked"})
        String status,
        @Schema(description = "Human-readable explanation of the current state.")
        String message,
        @Schema(description = "Stable upstream application user UUID for the requesting profile.")
        UUID appUserId,
        @Schema(description = "Stable upstream application user UUID for the comparison profile.")
        UUID otherUserId,
        @Schema(description = "Requested comparison period.")
        OurSongPeriodType periodType,
        @Schema(description = "Local period start used for the comparison window.")
        LocalDate periodStartLocal,
        @Schema(description = "Winning Spotify track id when a shared song exists.")
        String spotifyTrackId,
        @Schema(description = "Winning shared track name when available.")
        String trackName,
        @Schema(description = "Play count for the requesting user within the selected window.")
        Integer userPlayCount,
        @Schema(description = "Play count for the comparison user within the selected window.")
        Integer otherUserPlayCount,
        @Schema(description = "Combined play count used as the primary ranking signal.")
        Integer combinedPlayCount,
        @Schema(description = "Deterministic tie-break rule applied when multiple common songs tie.")
        String tieBreakRule
) {

    public static OurSongResponse unlinked(
            UUID appUserId,
            UUID otherUserId,
            OurSongPeriodType periodType,
            LocalDate periodStartLocal,
            String message
    ) {
        return new OurSongResponse(
                "unlinked",
                message,
                appUserId,
                otherUserId,
                periodType,
                periodStartLocal,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static OurSongResponse noCommonSong(
            UUID appUserId,
            UUID otherUserId,
            OurSongPeriodType periodType,
            LocalDate periodStartLocal
    ) {
        return new OurSongResponse(
                "no-common-song",
                "No common song was found for the requested period.",
                appUserId,
                otherUserId,
                periodType,
                periodStartLocal,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static OurSongResponse available(OurSongMatchView match) {
        return new OurSongResponse(
                "ready",
                "Shared-song data is available.",
                match.appUserId(),
                match.otherUserId(),
                match.periodType(),
                match.periodStartLocal(),
                match.spotifyTrackId(),
                match.trackName(),
                match.userPlayCount(),
                match.otherUserPlayCount(),
                match.combinedPlayCount(),
                match.tieBreakRule()
        );
    }
}
