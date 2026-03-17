package sotd.spotify;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotify")
public class SpotifyProperties {

    private URI baseUrl = URI.create("https://api.spotify.com/v1");
    private URI accountsBaseUrl = URI.create("https://accounts.spotify.com");
    private String clientId = "";
    private String clientSecret = "";
    private URI redirectUri = URI.create("http://localhost:8080/api/spotify/callback");
    private final Polling polling = new Polling();

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public URI getAccountsBaseUrl() {
        return accountsBaseUrl;
    }

    public void setAccountsBaseUrl(URI accountsBaseUrl) {
        this.accountsBaseUrl = accountsBaseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public URI getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(URI redirectUri) {
        this.redirectUri = redirectUri;
    }

    public Polling getPolling() {
        return polling;
    }

    public static class Polling {

        private Duration recentlyPlayedInterval = Duration.ofMinutes(2);
        private Duration currentPlaybackInterval = Duration.ofSeconds(20);

        public Duration getRecentlyPlayedInterval() {
            return recentlyPlayedInterval;
        }

        public void setRecentlyPlayedInterval(Duration recentlyPlayedInterval) {
            this.recentlyPlayedInterval = recentlyPlayedInterval;
        }

        public Duration getCurrentPlaybackInterval() {
            return currentPlaybackInterval;
        }

        public void setCurrentPlaybackInterval(Duration currentPlaybackInterval) {
            this.currentPlaybackInterval = currentPlaybackInterval;
        }
    }
}
