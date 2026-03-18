package sotd.spotify;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotify")
/**
 * External configuration for Spotify API integration.
 *
 * <p>This covers the auth endpoints, credentials, redirect URI, requested scopes, and planned polling
 * intervals for playback ingestion.
 */
public class SpotifyProperties {

    private URI baseUrl = URI.create("https://api.spotify.com/v1");
    private URI accountsBaseUrl = URI.create("https://accounts.spotify.com");
    private String clientId = "";
    private String clientSecret = "";
    private URI redirectUri = URI.create("http://127.0.0.1:8080/api/spotify/callback");
    private List<String> scopes = new ArrayList<>();
    private boolean showDialog;
    private Duration authStateTtl = Duration.ofMinutes(10);
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

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public boolean isShowDialog() {
        return showDialog;
    }

    public void setShowDialog(boolean showDialog) {
        this.showDialog = showDialog;
    }

    public Duration getAuthStateTtl() {
        return authStateTtl;
    }

    public void setAuthStateTtl(Duration authStateTtl) {
        this.authStateTtl = authStateTtl;
    }

    public Polling getPolling() {
        return polling;
    }

    /**
     * Polling intervals reserved for the future playback ingestion workers.
     */
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
