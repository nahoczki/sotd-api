package sotd.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sotd.crypto")
/**
 * External configuration for application-level encryption.
 *
 * <p>The current implementation uses a single Base64-encoded AES-256 key to encrypt Spotify refresh
 * tokens before they are stored in PostgreSQL.
 */
public class CryptoProperties {

    private String base64Key = "";

    public String getBase64Key() {
        return base64Key;
    }

    public void setBase64Key(String base64Key) {
        this.base64Key = base64Key;
    }
}
