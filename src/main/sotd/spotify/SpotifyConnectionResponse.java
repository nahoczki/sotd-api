package sotd.spotify;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Result returned after Spotify account linking succeeds.")
public record SpotifyConnectionResponse(
        @Schema(description = "Connection status.", allowableValues = {"connected"})
        String status,
        @Schema(description = "Stable upstream application user UUID.")
        UUID appUserId,
        @Schema(description = "Linked Spotify user id.")
        String spotifyUserId,
        @Schema(description = "Spotify display name.")
        String displayName,
        @Schema(description = "Granted Spotify scopes returned by the token exchange.")
        String grantedScope,
        @Schema(description = "When the current access token will expire.")
        Instant accessTokenExpiresAt
) {
}
