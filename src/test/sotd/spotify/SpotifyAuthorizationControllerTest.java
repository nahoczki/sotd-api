package sotd.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SpotifyAuthorizationControllerTest {

    @Test
    void connectReturnsRedirectToSpotifyAuthorizeUrl() {
        SpotifyAuthorizationService service = mock(SpotifyAuthorizationService.class);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(service.buildAuthorizationUri(appUserId)).thenReturn(URI.create("https://accounts.spotify.test/authorize"));
        SpotifyAuthorizationController controller = new SpotifyAuthorizationController(service);

        ResponseEntity<Void> response = controller.connect(appUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("https://accounts.spotify.test/authorize");
    }

    @Test
    void callbackDelegatesToService() {
        SpotifyAuthorizationService service = mock(SpotifyAuthorizationService.class);
        SpotifyConnectionResponse expected = new SpotifyConnectionResponse(
                "connected",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "spotify-user",
                "Luke",
                "user-read-private",
                Instant.parse("2026-03-17T21:00:00Z")
        );
        when(service.handleCallback("code-123", "state-123", null)).thenReturn(expected);
        SpotifyAuthorizationController controller = new SpotifyAuthorizationController(service);

        SpotifyConnectionResponse actual = controller.callback("code-123", "state-123", null);

        assertThat(actual).isEqualTo(expected);
        verify(service).handleCallback("code-123", "state-123", null);
    }

    @Test
    void getConnectionReturnsNotFoundWhenNoAccountLinked() {
        SpotifyAuthorizationService service = mock(SpotifyAuthorizationService.class);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(service.getCurrentConnection(appUserId)).thenReturn(Optional.empty());
        SpotifyAuthorizationController controller = new SpotifyAuthorizationController(service);

        ResponseEntity<SpotifyLinkedAccountView> response = controller.getConnection(appUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void disconnectReturnsNoContentAndDelegatesToService() {
        SpotifyAuthorizationService service = mock(SpotifyAuthorizationService.class);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SpotifyAuthorizationController controller = new SpotifyAuthorizationController(service);

        ResponseEntity<Void> response = controller.disconnect(appUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).disconnectCurrentConnection(appUserId);
    }
}
