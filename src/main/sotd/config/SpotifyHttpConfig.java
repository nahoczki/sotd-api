package sotd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import sotd.spotify.SpotifyProperties;

@Configuration
public class SpotifyHttpConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient spotifyApiRestClient(RestClient.Builder builder, SpotifyProperties properties) {
        return builder
                .baseUrl(properties.getBaseUrl().toString())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    RestClient spotifyAccountsRestClient(RestClient.Builder builder, SpotifyProperties properties) {
        return builder
                .baseUrl(properties.getAccountsBaseUrl().toString())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
