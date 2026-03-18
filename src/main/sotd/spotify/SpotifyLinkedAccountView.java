package sotd.spotify;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Linked Spotify account summary for a specific application user.")
public record SpotifyLinkedAccountView(
        @Schema(description = "Stable upstream application user UUID.")
        UUID appUserId,
        @Schema(description = "Linked Spotify user id.")
        String spotifyUserId,
        @Schema(description = "Spotify display name.")
        String displayName,
        @Schema(description = "Currently stored Spotify scopes.")
        String scope,
        @Schema(description = "Internal account linkage state.", allowableValues = {"ACTIVE", "REAUTH_REQUIRED", "DISCONNECTED"})
        String status,
        @Schema(description = "IANA timezone used for local-day rollups.")
        String timezone,
        @Schema(description = "When the current access token will expire.")
        Instant accessTokenExpiresAt,
        @Schema(description = "When the linked account row was created.")
        Instant createdAt,
        @Schema(description = "When the linked account row was last updated.")
        Instant updatedAt
) {
}
