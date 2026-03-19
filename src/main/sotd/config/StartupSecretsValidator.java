package sotd.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import sotd.auth.UpstreamAuthProperties;
import sotd.crypto.CryptoProperties;
import sotd.spotify.SpotifyProperties;

/**
 * Fails fast on missing production-critical secrets and auth configuration when strict startup validation is enabled.
 */
@Component
public class StartupSecretsValidator implements SmartInitializingSingleton {

    private final StartupValidationProperties startupValidationProperties;
    private final SpotifyProperties spotifyProperties;
    private final CryptoProperties cryptoProperties;
    private final UpstreamAuthProperties upstreamAuthProperties;

    public StartupSecretsValidator(
            StartupValidationProperties startupValidationProperties,
            SpotifyProperties spotifyProperties,
            CryptoProperties cryptoProperties,
            UpstreamAuthProperties upstreamAuthProperties
    ) {
        this.startupValidationProperties = startupValidationProperties;
        this.spotifyProperties = spotifyProperties;
        this.cryptoProperties = cryptoProperties;
        this.upstreamAuthProperties = upstreamAuthProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate();
    }

    void validate() {
        if (!startupValidationProperties.isEnabled()) {
            return;
        }

        List<String> issues = new ArrayList<>();
        requireText(spotifyProperties.getClientId(), "SPOTIFY_CLIENT_ID", issues);
        requireText(spotifyProperties.getClientSecret(), "SPOTIFY_CLIENT_SECRET", issues);
        validateRedirectUri(spotifyProperties.getRedirectUri(), issues);
        validateCryptoKey(cryptoProperties.getBase64Key(), issues);

        if (upstreamAuthProperties.isEnabled()) {
            requireText(upstreamAuthProperties.getSharedSecret(), "SOTD_UPSTREAM_AUTH_SHARED_SECRET", issues);
            requireText(upstreamAuthProperties.getIssuer(), "SOTD_UPSTREAM_AUTH_ISSUER", issues);
            requireText(upstreamAuthProperties.getAudience(), "SOTD_UPSTREAM_AUTH_AUDIENCE", issues);
        }

        if (!issues.isEmpty()) {
            throw new IllegalStateException("Startup validation failed:\n - " + String.join("\n - ", issues));
        }
    }

    private static void requireText(String value, String envVar, List<String> issues) {
        if (!StringUtils.hasText(value)) {
            issues.add(envVar + " must be set.");
        }
    }

    private static void validateRedirectUri(URI redirectUri, List<String> issues) {
        if (redirectUri == null) {
            issues.add("SPOTIFY_REDIRECT_URI must be set.");
            return;
        }

        String host = redirectUri.getHost();
        if (!StringUtils.hasText(host)) {
            issues.add("SPOTIFY_REDIRECT_URI must contain a valid host.");
            return;
        }

        if (isLoopbackHost(host)) {
            issues.add("SPOTIFY_REDIRECT_URI must not use a loopback host when strict startup validation is enabled.");
        }
    }

    private static void validateCryptoKey(String base64Key, List<String> issues) {
        if (!StringUtils.hasText(base64Key)) {
            issues.add("SOTD_CRYPTO_BASE64_KEY must be set.");
            return;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key);
        }
        catch (IllegalArgumentException ex) {
            issues.add("SOTD_CRYPTO_BASE64_KEY must be valid Base64.");
            return;
        }

        if (decoded.length != 32) {
            issues.add("SOTD_CRYPTO_BASE64_KEY must decode to exactly 32 bytes.");
        }
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }
}
