package sotd.config;

import java.util.Arrays;
import java.util.Map;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
            UpstreamAuthProperties upstreamAuthProperties
    ) {
        return builder -> builder
                .withDetail("app", appDetails(environment))
                .withDetail("docs", Map.of(
                        "swaggerUiPath", "/docs",
                        "openApiPath", "/openapi"
                ))
                .withDetail("features", Map.of(
                        "spotifyConnect", true,
                        "spotifyUnlink", true,
                        "songOfDay", true,
                        "ourSong", true
                ))
                .withDetail("auth", Map.of(
                        "upstreamJwtEnabled", upstreamAuthProperties.isEnabled(),
                        "headerName", upstreamAuthProperties.getHeaderName(),
                        "connectQueryParameter", upstreamAuthProperties.getQueryParameterName()
                ))
                .withDetail("polling", Map.of(
                        "recentlyPlayedInterval", spotifyProperties.getPolling().getRecentlyPlayedInterval().toString(),
                        "currentPlaybackInterval", spotifyProperties.getPolling().getCurrentPlaybackInterval().toString(),
                        "currentPlaybackWorkerImplemented", false
                ));
    }

    private static Map<String, Object> appDetails(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        return Map.of(
                "name", environment.getProperty("spring.application.name", "sotd-api"),
                "description", environment.getProperty("info.app.description", "Spotify polling and music insights API."),
                "version", environment.getProperty("info.app.version", "0.0.1-SNAPSHOT"),
                "activeProfiles", activeProfiles.length > 0 ? Arrays.asList(activeProfiles) : java.util.List.of("default")
        );
    }
}
