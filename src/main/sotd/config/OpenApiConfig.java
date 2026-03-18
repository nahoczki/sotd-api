package sotd.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for frontend and upstream integration consumers.
 */
@Configuration
public class OpenApiConfig {

    public static final String UPSTREAM_HEADER_AUTH_SCHEME = "upstreamHeaderAuth";
    public static final String UPSTREAM_QUERY_AUTH_SCHEME = "upstreamQueryAuth";

    @Bean
    OpenAPI sotdOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SOTD API")
                        .version("v1")
                        .description("User-scoped Spotify polling and song-of-the-day API.")
                        .contact(new Contact().name("SOTD API")))
                .components(new Components()
                        .addSecuritySchemes(
                                UPSTREAM_HEADER_AUTH_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Short-lived upstream-issued JWT for server-to-server user-scoped requests.")
                        )
                        .addSecuritySchemes(
                                UPSTREAM_QUERY_AUTH_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.QUERY)
                                        .name("upstreamAuth")
                                        .description("Short-lived upstream-issued JWT for browser redirects into the Spotify connect flow.")
                        ))
                .addTagsItem(new Tag().name("song-of-the-day").description("User-scoped winner reads for profile pages."))
                .addTagsItem(new Tag().name("our-song").description("Pairwise shared-song reads for two profile pages."))
                .addTagsItem(new Tag().name("spotify-auth").description("Spotify account linking and linked-account inspection."));
    }
}
