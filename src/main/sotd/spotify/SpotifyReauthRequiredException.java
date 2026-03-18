package sotd.spotify;

/**
 * Raised when Spotify refresh-token exchange indicates the linked account must be reauthorized.
 */
public class SpotifyReauthRequiredException extends RuntimeException {

    public SpotifyReauthRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
