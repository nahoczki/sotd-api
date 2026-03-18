package sotd.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void openApiBeanIncludesExpectedSecuritySchemesAndMetadata() {
        OpenAPI openAPI = new OpenApiConfig().sotdOpenApi();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("SOTD API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKeys(OpenApiConfig.UPSTREAM_HEADER_AUTH_SCHEME, OpenApiConfig.UPSTREAM_QUERY_AUTH_SCHEME);
        assertThat(openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.UPSTREAM_HEADER_AUTH_SCHEME).getType())
                .isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.UPSTREAM_HEADER_AUTH_SCHEME).getScheme())
                .isEqualTo("bearer");
        assertThat(openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.UPSTREAM_QUERY_AUTH_SCHEME).getIn())
                .isEqualTo(SecurityScheme.In.QUERY);
    }
}
