package sotd.spotify.client;

import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
/**
 * Thin client for Spotify Accounts API operations such as token exchange.
 */
public class SpotifyAccountsClient {

    private final RestClient spotifyAccountsRestClient;

    public SpotifyAccountsClient(@Qualifier("spotifyAccountsRestClient") RestClient spotifyAccountsRestClient) {
        this.spotifyAccountsRestClient = spotifyAccountsRestClient;
    }

    /**
     * Exchanges a Spotify authorization code for an access token and refresh token.
     */
    public SpotifyTokenResponse exchangeAuthorizationCode(
            String clientId,
            String clientSecret,
            String code,
            URI redirectUri
    ) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri.toString());

        SpotifyTokenResponse response = spotifyAccountsRestClient.post()
                .uri("/api/token")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(SpotifyTokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Spotify token exchange returned an empty response.");
        }
        return response;
    }

    private static String basicAuth(String clientId, String clientSecret) {
        return "Basic " + HttpHeaders.encodeBasicAuth(clientId, clientSecret, null);
    }
}
