package sotd.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;
import sotd.auth.UpstreamAuthProperties;
import sotd.spotify.SpotifyProperties;

class ActuatorInfoConfigTest {

    private final ActuatorInfoConfig config = new ActuatorInfoConfig();

    @Test
    void infoContributorIncludesProxyForwardingDetails() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "sotd-api")
                .withProperty("info.app.description", "Spotify polling and music insights API.")
                .withProperty("info.app.version", "1.0.0")
                .withProperty("server.forward-headers-strategy", "framework")
                .withProperty("server.tomcat.redirect-context-root", "false");

        Info.Builder builder = new Info.Builder();
        config.sotdInfoContributor(environment, new SpotifyProperties(), new UpstreamAuthProperties())
                .contribute(builder);

        Map<String, Object> details = builder.build().getDetails();
        Map<String, Object> proxyDetails = castMap(details.get("proxy"));

        assertThat(details).containsKey("proxy");
        assertThat(proxyDetails)
                .containsEntry("forwardHeadersStrategy", "framework")
                .containsEntry("tomcatRedirectContextRoot", false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
