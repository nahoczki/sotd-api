package sotd.auth;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Enforces that user-scoped routes are called with a valid upstream-issued auth token.
 */
@Component
public class UpstreamAuthInterceptor implements HandlerInterceptor {

    static final String VERIFIED_APP_USER_ID_ATTRIBUTE = "verifiedAppUserId";

    private final UpstreamAuthProperties upstreamAuthProperties;
    private final UpstreamRequestTokenService upstreamRequestTokenService;

    public UpstreamAuthInterceptor(
            UpstreamAuthProperties upstreamAuthProperties,
            UpstreamRequestTokenService upstreamRequestTokenService
    ) {
        this.upstreamAuthProperties = upstreamAuthProperties;
        this.upstreamRequestTokenService = upstreamRequestTokenService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, Object handler) {
        if (!upstreamAuthProperties.isEnabled()) {
            return true;
        }

        Map<String, String> uriVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (uriVariables == null || !uriVariables.containsKey("appUserId")) {
            return true;
        }

        UUID pathAppUserId = parsePathUserId(uriVariables.get("appUserId"));
        String token = resolveToken(request);
        UpstreamRequestTokenService.VerifiedUpstreamRequest verifiedRequest = upstreamRequestTokenService.verify(token);

        if (!verifiedRequest.appUserId().equals(pathAppUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Upstream authorization token does not match the requested user.");
        }

        request.setAttribute(VERIFIED_APP_USER_ID_ATTRIBUTE, verifiedRequest.appUserId());
        return true;
    }

    private String resolveToken(jakarta.servlet.http.HttpServletRequest request) {
        String headerToken = request.getHeader(upstreamAuthProperties.getHeaderName());
        if (StringUtils.hasText(headerToken)) {
            return headerToken;
        }
        return request.getParameter(upstreamAuthProperties.getQueryParameterName());
    }

    private static UUID parsePathUserId(String value) {
        try {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested user id is invalid.");
        }
    }
}
