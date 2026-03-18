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
    void preHandleAcceptsHeaderTokenForMatchingUser() throws Exception {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UpstreamRequestTokenService tokenService = tokenService();
        UpstreamAuthInterceptor interceptor = new UpstreamAuthInterceptor(configuredProperties(), tokenService);
        String token = tokenService.createToken(appUserId, Instant.parse("2026-03-19T00:05:00Z"));

        MockHttpServletRequest request = requestFor(appUserId);
        request.addHeader("X-SOTD-UPSTREAM-AUTH", token);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute(UpstreamAuthInterceptor.VERIFIED_APP_USER_ID_ATTRIBUTE)).isEqualTo(appUserId);
    }

    @Test
    void preHandleAcceptsQueryParameterTokenForBrowserRedirectFlow() throws Exception {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UpstreamRequestTokenService tokenService = tokenService();
        UpstreamAuthInterceptor interceptor = new UpstreamAuthInterceptor(configuredProperties(), tokenService);
        String token = tokenService.createToken(appUserId, Instant.parse("2026-03-19T00:05:00Z"));

        MockHttpServletRequest request = requestFor(appUserId);
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

        MockHttpServletRequest request = requestFor(pathUserId);
        request.addHeader("X-SOTD-UPSTREAM-AUTH", token);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("does not match");
    }

    @Test
    void preHandleRejectsMissingToken() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UpstreamAuthInterceptor interceptor = new UpstreamAuthInterceptor(configuredProperties(), tokenService());

        MockHttpServletRequest request = requestFor(appUserId);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("Missing upstream authorization token");
    }

    private static MockHttpServletRequest requestFor(UUID appUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/" + appUserId + "/song-of-the-day");
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
        properties.setClockSkew(Duration.ofSeconds(30));
        return properties;
    }
}
