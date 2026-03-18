package sotd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import sotd.crypto.CryptoProperties;
import sotd.spotify.SpotifyProperties;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({SpotifyProperties.class, CryptoProperties.class})
/**
 * Entry point for the SOTD API.
 *
 * <p>The application currently runs as a single Spring Boot service with scheduled jobs enabled for
 * future Spotify polling work.
 */
public class SotdApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SotdApiApplication.class, args);
    }
}
