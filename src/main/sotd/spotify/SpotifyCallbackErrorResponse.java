package sotd.spotify;

import java.time.Instant;
import java.util.UUID;

/**
 * Stable JSON error shape returned from the Spotify OAuth callback route.
 */
public record SpotifyCallbackErrorResponse(
        String status,
        String errorCode,
        String stage,
        String message,
        UUID appUserId,
        Instant timestamp
) {
    public static SpotifyCallbackErrorResponse from(SpotifyCallbackException ex, Instant timestamp) {
        return new SpotifyCallbackErrorResponse(
                "error",
                ex.getErrorCode(),
                ex.getStage().name(),
                ex.getUserMessage(),
                ex.getAppUserId(),
                timestamp
        );
    }
}
