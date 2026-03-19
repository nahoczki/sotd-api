package sotd.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
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
        config.sotdInfoContributor(environment, new SpotifyProperties(), new UpstreamAuthProperties(), null, null)
                .contribute(builder);

        Map<String, Object> details = builder.build().getDetails();
        Map<String, Object> proxyDetails = castMap(details.get("proxy"));

        assertThat(details).containsKey("proxy");
        assertThat(proxyDetails)
                .containsEntry("forwardHeadersStrategy", "framework")
                .containsEntry("tomcatRedirectContextRoot", false);
    }

    @Test
    void infoContributorIncludesBuildAndGitMetadataWhenAvailable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "sotd-api")
                .withProperty("info.app.description", "Spotify polling and music insights API.")
                .withProperty("info.image.tag", "sotd-api:1.2.3");

        BuildProperties buildProperties = new BuildProperties(properties(
                "group", "sotd",
                "artifact", "sotd-api",
                "name", "sotd-api",
                "version", "1.2.3",
                "time", "2026-03-18T22:15:00Z"
        ));
        GitProperties gitProperties = new GitProperties(properties(
                "branch", "main",
                "commit.id", "1234567890abcdef1234567890abcdef12345678",
                "commit.id.abbrev", "1234567890ab",
                "dirty", "false"
        ));

        Info.Builder builder = new Info.Builder();
        config.sotdInfoContributor(
                        environment,
                        new SpotifyProperties(),
                        new UpstreamAuthProperties(),
                        buildProperties,
                        gitProperties
                )
                .contribute(builder);

        Map<String, Object> details = builder.build().getDetails();
        Map<String, Object> appDetails = castMap(details.get("app"));
        Map<String, Object> buildDetails = castMap(details.get("build"));
        Map<String, Object> gitDetails = castMap(details.get("git"));

        assertThat(appDetails).containsEntry("version", "1.2.3");
        assertThat(buildDetails)
                .containsEntry("artifact", "sotd-api")
                .containsEntry("group", "sotd")
                .containsEntry("name", "sotd-api")
                .containsEntry("version", "1.2.3")
                .containsEntry("imageTag", "sotd-api:1.2.3");
        assertThat(gitDetails)
                .containsEntry("branch", "main")
                .containsEntry("commitSha", "1234567890abcdef1234567890abcdef12345678")
                .containsEntry("commitShortSha", "1234567890ab")
                .containsEntry("dirty", "false");
    }

    private static java.util.Properties properties(String... entries) {
        java.util.Properties properties = new java.util.Properties();
        for (int index = 0; index < entries.length; index += 2) {
            properties.setProperty(entries[index], entries[index + 1]);
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
