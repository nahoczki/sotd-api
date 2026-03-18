package sotd.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

class UpstreamAuthInterceptorTest {

    @Test
    void preHandleAcceptsHeaderTokenForMatchingUser() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UpstreamRequestTokenService tokenService = tokenService();
        UpstreamAuthInterceptor interceptor = new UpstreamAuthInterceptor(configuredProperties(), tokenService);
        String token = tokenService.createToken(appUserId, Instant.parse("2026-03-19T00:05:00Z"));

        MockHttpServletRequest request = requestFor(appUserId, "/api/users/" + appUserId + "/top-song");
        request.addHeader("Authorization", "Bearer " + token);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute(UpstreamAuthInterceptor.VERIFIED_APP_USER_ID_ATTRIBUTE)).isEqualTo(appUserId);
    }

    @Test
    void preHandleAcceptsQueryParameterTokenForBrowserRedirectFlow() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UpstreamRequestTokenService tokenService = tokenService();
        UpstreamAuthInterceptor interceptor = new UpstreamAuthInterceptor(configuredProperties(), tokenService);
        String token = tokenService.createToken(appUserId, Instant.parse("2026-03-19T00:05:00Z"));

        MockHttpServletRequest request = requestFor(appUserId, "/api/users/" + appUserId + "/spotify/connect");
        request.addParameter("upstreamAuth", token);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void preHandleRejectsTokenForDifferentUser() {
        UUID pathUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID tokenUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UpstreamRequestTokenService tokenService = tokenService();
        UpstreamAuthInterceptor interceptor = new UpstreamAuthInterceptor(configuredProperties(), tokenService);
        String token = tokenService.createToken(tokenUserId, Instant.parse("2026-03-19T00:05:00Z"));

        MockHttpServletRequest request = requestFor(pathUserId, "/api/users/" + pathUserId + "/top-song");
        request.addHeader("Authorization", "Bearer " + token);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("does not match");
    }

    @Test
    void preHandleRejectsMissingToken() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UpstreamAuthInterceptor interceptor = new UpstreamAuthInterceptor(configuredProperties(), tokenService());

        MockHttpServletRequest request = requestFor(appUserId, "/api/users/" + appUserId + "/top-song");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("Missing upstream authorization token");
    }

    private static MockHttpServletRequest requestFor(UUID appUserId, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("appUserId", appUserId.toString()));
        return request;
    }

    private static UpstreamRequestTokenService tokenService() {
        return new UpstreamRequestTokenService(
                configuredProperties(),
                Clock.fixed(Instant.parse("2026-03-19T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static UpstreamAuthProperties configuredProperties() {
        UpstreamAuthProperties properties = new UpstreamAuthProperties();
        properties.setEnabled(true);
        properties.setSharedSecret("shared-secret-for-tests");
        properties.setIssuer("accounts-api");
        properties.setAudience("sotd-api");
        properties.setClockSkew(Duration.ofSeconds(30));
        properties.setHeaderName("Authorization");
        return properties;
    }
}
