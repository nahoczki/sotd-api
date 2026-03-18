package sotd.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import sotd.auth.UpstreamAuthProperties;
import sotd.spotify.SpotifyProperties;

/**
 * Supplies a deliberate non-empty actuator info payload for operators and integrators.
 */
@Configuration
public class ActuatorInfoConfig {

    @Bean
    InfoContributor sotdInfoContributor(
            Environment environment,
            SpotifyProperties spotifyProperties,
            UpstreamAuthProperties upstreamAuthProperties,
            @Nullable BuildProperties buildProperties,
            @Nullable GitProperties gitProperties
    ) {
        return builder -> {
            builder.withDetail("app", appDetails(environment, buildProperties))
                    .withDetail("docs", Map.of(
                            "swaggerUiPath", "/docs",
                            "openApiPath", "/openapi"
                    ))
                    .withDetail("features", Map.of(
                            "spotifyConnect", true,
                            "spotifyUnlink", true,
                            "topSong", true,
                            "ourSong", true
                    ))
                    .withDetail("auth", Map.of(
                            "upstreamJwtEnabled", upstreamAuthProperties.isEnabled(),
                            "headerName", upstreamAuthProperties.getHeaderName(),
                            "connectQueryParameter", upstreamAuthProperties.getQueryParameterName()
                    ))
                    .withDetail("proxy", Map.of(
                            "forwardHeadersStrategy", environment.getProperty("server.forward-headers-strategy", "none"),
                            "tomcatRedirectContextRoot",
                            environment.getProperty("server.tomcat.redirect-context-root", Boolean.class, true)
                    ))
                    .withDetail("polling", Map.of(
                            "recentlyPlayedInterval", spotifyProperties.getPolling().getRecentlyPlayedInterval().toString(),
                            "currentPlaybackInterval", spotifyProperties.getPolling().getCurrentPlaybackInterval().toString(),
                            "currentPlaybackWorkerImplemented", false
                    ));

            Map<String, Object> buildDetails = buildDetails(environment, buildProperties);
            if (!buildDetails.isEmpty()) {
                builder.withDetail("build", buildDetails);
            }

            Map<String, Object> gitDetails = gitDetails(gitProperties);
            if (!gitDetails.isEmpty()) {
                builder.withDetail("git", gitDetails);
            }
        };
    }

    private static Map<String, Object> appDetails(Environment environment, @Nullable BuildProperties buildProperties) {
        String[] activeProfiles = environment.getActiveProfiles();
        return Map.of(
                "name", environment.getProperty("spring.application.name", "sotd-api"),
                "description", environment.getProperty("info.app.description", "Spotify polling and music insights API."),
                "version", buildProperties != null
                        ? buildProperties.getVersion()
                        : environment.getProperty("info.app.version", "0.0.1-SNAPSHOT"),
                "activeProfiles", activeProfiles.length > 0 ? Arrays.asList(activeProfiles) : java.util.List.of("default")
        );
    }

    private static Map<String, Object> buildDetails(Environment environment, @Nullable BuildProperties buildProperties) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (buildProperties != null) {
            details.put("artifact", buildProperties.getArtifact());
            details.put("group", buildProperties.getGroup());
            details.put("name", buildProperties.getName());
            details.put("version", buildProperties.getVersion());
            details.put("time", buildProperties.getTime().toString());
        }

        String imageTag = environment.getProperty("info.image.tag");
        if (imageTag != null && !imageTag.isBlank() && !"unknown".equalsIgnoreCase(imageTag)) {
            details.put("imageTag", imageTag);
        }
        return details;
    }

    private static Map<String, Object> gitDetails(@Nullable GitProperties gitProperties) {
        if (gitProperties == null) {
            return Map.of();
        }

        Map<String, Object> details = new LinkedHashMap<>();
        addIfPresent(details, "branch", gitProperties.getBranch());
        addIfPresent(details, "commitSha", gitProperties.getCommitId());
        addIfPresent(details, "commitShortSha", gitProperties.getShortCommitId());
        addIfPresent(details, "commitTime", gitProperties.getCommitTime());
        addIfPresent(details, "dirty", gitProperties.toPropertySource().getProperty("dirty"));
        return details;
    }

    private static void addIfPresent(Map<String, Object> target, String key, @Nullable Object value) {
        if (value == null) {
            return;
        }
        String stringValue = value.toString();
        if (!stringValue.isBlank() && !"unknown".equalsIgnoreCase(stringValue)) {
            target.put(key, stringValue);
        }
    }
}
