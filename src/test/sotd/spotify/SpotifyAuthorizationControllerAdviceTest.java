package sotd.spotify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SpotifyAuthorizationControllerAdviceTest {

    @Test
    void handleSpotifyCallbackExceptionReturnsStructuredJsonBody() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC);
        SpotifyAuthorizationControllerAdvice advice = new SpotifyAuthorizationControllerAdvice(clock);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SpotifyCallbackException exception = SpotifyCallbackException.authorizationDenied(
                appUserId,
                "Spotify authorization was denied or cancelled."
        );

        ResponseEntity<SpotifyCallbackErrorResponse> response = advice.handleSpotifyCallbackException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new SpotifyCallbackErrorResponse(
                "error",
                "spotify_callback_authorization_denied",
                "AUTHORIZATION_DENIED",
                "Spotify authorization was denied or cancelled.",
                appUserId,
                Instant.parse("2026-03-18T00:00:00Z")
        ));
    }
}
