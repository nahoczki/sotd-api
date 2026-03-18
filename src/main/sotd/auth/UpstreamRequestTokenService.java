package sotd.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Issues and verifies short-lived HMAC-signed tokens minted by the upstream application backend.
 *
 * <p>Token format:
 *
 * <ul>
 *   <li>payload: `sub={uuid}&exp={epochSeconds}`
 *   <li>token: `base64url(payload).base64url(hmacSha256(payload, sharedSecret))`
 * </ul>
 */
@Service
public class UpstreamRequestTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final UpstreamAuthProperties upstreamAuthProperties;
    private final Clock clock;

    public UpstreamRequestTokenService(UpstreamAuthProperties upstreamAuthProperties, Clock clock) {
        this.upstreamAuthProperties = upstreamAuthProperties;
        this.clock = clock;
    }

    /**
     * Generates a signed token for a specific app user. This is primarily useful for tests and local
     * development until the real upstream backend is integrated.
     */
    public String createToken(UUID appUserId, Instant expiresAt) {
        requireConfiguredSharedSecret();
        String payload = "sub=" + appUserId + "&exp=" + expiresAt.getEpochSecond();
        String payloadPart = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signaturePart = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sign(payload));
        return payloadPart + "." + signaturePart;
    }

    public VerifiedUpstreamRequest verify(String token) {
        requireConfiguredSharedSecret();
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing upstream authorization token.");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token is invalid.");
        }

        byte[] payloadBytes = decodeBase64Url(parts[0]);
        byte[] signatureBytes = decodeBase64Url(parts[1]);
        if (!MessageDigest.isEqual(sign(payloadBytes), signatureBytes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token signature is invalid.");
        }

        Map<String, String> claims = parseClaims(new String(payloadBytes, StandardCharsets.UTF_8));
        UUID appUserId = parseAppUserId(claims.get("sub"));
        Instant expiresAt = parseExpiry(claims.get("exp"));
        Instant now = clock.instant();
        if (expiresAt.plus(upstreamAuthProperties.getClockSkew()).isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token has expired.");
        }

        return new VerifiedUpstreamRequest(appUserId, expiresAt);
    }

    private void requireConfiguredSharedSecret() {
        if (!StringUtils.hasText(upstreamAuthProperties.getSharedSecret())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SOTD_UPSTREAM_AUTH_SHARED_SECRET is not configured."
            );
        }
    }

    private byte[] sign(String payload) {
        return sign(payload.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sign(byte[] payloadBytes) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    upstreamAuthProperties.getSharedSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);
            return mac.doFinal(payloadBytes);
        }
        catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to sign upstream authorization token.", ex);
        }
    }

    private static byte[] decodeBase64Url(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token is invalid.");
        }
    }

    private static Map<String, String> parseClaims(String payload) {
        Map<String, String> claims = new LinkedHashMap<>();
        for (String pair : payload.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                claims.put(parts[0], parts[1]);
            }
        }
        return claims;
    }

    private static UUID parseAppUserId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token is missing a subject.");
        }
        try {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token subject is invalid.");
        }
    }

    private static Instant parseExpiry(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token is missing an expiry.");
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        }
        catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token expiry is invalid.");
        }
    }

    public record VerifiedUpstreamRequest(
            UUID appUserId,
            Instant expiresAt
    ) {
    }
}
