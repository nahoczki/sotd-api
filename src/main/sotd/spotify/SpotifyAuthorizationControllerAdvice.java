package sotd.spotify;

import java.time.Clock;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts callback-specific failures into a stable JSON response for browser and proxy clients.
 */
@RestControllerAdvice(assignableTypes = SpotifyAuthorizationController.class)
public class SpotifyAuthorizationControllerAdvice {

    private final Clock clock;

    public SpotifyAuthorizationControllerAdvice(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(SpotifyCallbackException.class)
    ResponseEntity<SpotifyCallbackErrorResponse> handleSpotifyCallbackException(SpotifyCallbackException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(SpotifyCallbackErrorResponse.from(ex, clock.instant()));
    }
}
