package sotd.spotify.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
/**
 * Thin client for Spotify Web API calls executed on behalf of the linked user.
 */
public class SpotifyApiClient {

    private final RestClient spotifyApiRestClient;

    public SpotifyApiClient(@Qualifier("spotifyApiRestClient") RestClient spotifyApiRestClient) {
        this.spotifyApiRestClient = spotifyApiRestClient;
    }

    /**
     * Fetches the current Spotify user profile for the supplied bearer token.
     */
    public SpotifyCurrentUserProfile getCurrentUserProfile(String accessToken) {
        SpotifyCurrentUserProfile response = spotifyApiRestClient.get()
                .uri("/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(SpotifyCurrentUserProfile.class);

        if (response == null || response.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Spotify profile response was empty.");
        }
        return response;
    }

    /**
     * Fetches the user's recently played history after the supplied cursor.
     */
    public SpotifyRecentlyPlayedResponse getRecentlyPlayed(String accessToken, Long after, int limit) {
        RequestHeadersUriSpec<?> request = spotifyApiRestClient.get();
        SpotifyRecentlyPlayedResponse response = request
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/me/player/recently-played")
                            .queryParam("limit", limit);
                    if (after != null) {
                        builder.queryParam("after", after);
                    }
                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(SpotifyRecentlyPlayedResponse.class);

        if (response == null || response.items() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Spotify recently-played response was empty.");
        }
        return response;
    }
}
