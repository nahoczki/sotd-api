package sotd.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import sotd.auth.UpstreamAuthProperties;
import sotd.crypto.CryptoProperties;
import sotd.spotify.SpotifyProperties;

class StartupSecretsValidatorTest {

    @Test
    void validateDoesNothingWhenStrictValidationIsDisabled() {
        StartupValidationProperties startupValidationProperties = new StartupValidationProperties();
        SpotifyProperties spotifyProperties = new SpotifyProperties();
        CryptoProperties cryptoProperties = new CryptoProperties();
        UpstreamAuthProperties upstreamAuthProperties = new UpstreamAuthProperties();

        StartupSecretsValidator validator = new StartupSecretsValidator(
                startupValidationProperties,
                spotifyProperties,
                cryptoProperties,
                upstreamAuthProperties
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void validateFailsWhenStrictValidationIsEnabledAndRequiredValuesAreMissing() {
        StartupValidationProperties startupValidationProperties = new StartupValidationProperties();
        startupValidationProperties.setEnabled(true);
        SpotifyProperties spotifyProperties = new SpotifyProperties();
        CryptoProperties cryptoProperties = new CryptoProperties();
        UpstreamAuthProperties upstreamAuthProperties = new UpstreamAuthProperties();
        upstreamAuthProperties.setEnabled(true);

        StartupSecretsValidator validator = new StartupSecretsValidator(
                startupValidationProperties,
                spotifyProperties,
                cryptoProperties,
                upstreamAuthProperties
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPOTIFY_CLIENT_ID")
                .hasMessageContaining("SPOTIFY_CLIENT_SECRET")
                .hasMessageContaining("SPOTIFY_REDIRECT_URI")
                .hasMessageContaining("SOTD_CRYPTO_BASE64_KEY")
                .hasMessageContaining("SOTD_UPSTREAM_AUTH_SHARED_SECRET")
                .hasMessageContaining("SOTD_UPSTREAM_AUTH_ISSUER")
                .hasMessageContaining("SOTD_UPSTREAM_AUTH_AUDIENCE");
    }

    @Test
    void validateAllowsDisabledUpstreamAuthWhenOtherRequiredValuesArePresent() {
        StartupValidationProperties startupValidationProperties = new StartupValidationProperties();
        startupValidationProperties.setEnabled(true);
        SpotifyProperties spotifyProperties = new SpotifyProperties();
        spotifyProperties.setClientId("spotify-client-id");
        spotifyProperties.setClientSecret("spotify-client-secret");
        spotifyProperties.setRedirectUri(URI.create("https://app.example.com/api/spotify/callback"));
        CryptoProperties cryptoProperties = new CryptoProperties();
        cryptoProperties.setBase64Key(base64KeyOf32Bytes());
        UpstreamAuthProperties upstreamAuthProperties = new UpstreamAuthProperties();
        upstreamAuthProperties.setEnabled(false);

        StartupSecretsValidator validator = new StartupSecretsValidator(
                startupValidationProperties,
                spotifyProperties,
                cryptoProperties,
                upstreamAuthProperties
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void validateFailsWhenCryptoKeyIsNotThirtyTwoBytes() {
        StartupValidationProperties startupValidationProperties = new StartupValidationProperties();
        startupValidationProperties.setEnabled(true);
        SpotifyProperties spotifyProperties = new SpotifyProperties();
        spotifyProperties.setClientId("spotify-client-id");
        spotifyProperties.setClientSecret("spotify-client-secret");
        spotifyProperties.setRedirectUri(URI.create("https://app.example.com/api/spotify/callback"));
        CryptoProperties cryptoProperties = new CryptoProperties();
        cryptoProperties.setBase64Key(Base64.getEncoder().encodeToString(new byte[16]));
        UpstreamAuthProperties upstreamAuthProperties = new UpstreamAuthProperties();
        upstreamAuthProperties.setEnabled(false);

        StartupSecretsValidator validator = new StartupSecretsValidator(
                startupValidationProperties,
                spotifyProperties,
                cryptoProperties,
                upstreamAuthProperties
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 32 bytes");
    }

    private static String base64KeyOf32Bytes() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }
}
