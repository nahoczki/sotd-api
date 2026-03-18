package sotd.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Issues and verifies short-lived JWTs minted by the upstream application backend.
 */
@Service
public class UpstreamRequestTokenService {

    private final UpstreamAuthProperties upstreamAuthProperties;
    private final Clock clock;

    public UpstreamRequestTokenService(UpstreamAuthProperties upstreamAuthProperties, Clock clock) {
        this.upstreamAuthProperties = upstreamAuthProperties;
        this.clock = clock;
    }

    /**
     * Generates a signed JWT for a specific app user. This remains useful for tests and local
     * development while the upstream backend integration is being finalized.
     */
    public String createToken(UUID appUserId, Instant expiresAt) {
        Instant issuedAt = clock.instant();
        return JWT.create()
                .withIssuer(requireConfiguredIssuer())
                .withAudience(requireConfiguredAudience())
                .withSubject(appUserId.toString())
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm());
    }

    public VerifiedUpstreamRequest verify(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing upstream authorization token.");
        }

        DecodedJWT decodedJwt;
        try {
            decodedJwt = verifier().verify(token);
        }
        catch (JWTVerificationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token is invalid.", ex);
        }

        UUID appUserId = parseAppUserId(decodedJwt.getSubject());
        Instant expiresAt = requireTimestamp(decodedJwt.getExpiresAt(), "expiry");
        Instant issuedAt = requireTimestamp(decodedJwt.getIssuedAt(), "issued-at time");
        Instant now = clock.instant();

        if (issuedAt.isAfter(now.plus(upstreamAuthProperties.getClockSkew()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token issued-at time is invalid.");
        }

        if (expiresAt.plus(upstreamAuthProperties.getClockSkew()).isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token has expired.");
        }

        return new VerifiedUpstreamRequest(appUserId, expiresAt);
    }

    private Algorithm algorithm() {
        return Algorithm.HMAC256(requireConfiguredSharedSecret());
    }

    private JWTVerifier verifier() {
        JWTVerifier.BaseVerification verification = (JWTVerifier.BaseVerification) JWT.require(algorithm())
                .withIssuer(requireConfiguredIssuer())
                .withAudience(requireConfiguredAudience())
                .withClaimPresence("sub")
                .withClaimPresence("iat")
                .withClaimPresence("exp")
                .acceptLeeway(upstreamAuthProperties.getClockSkew().getSeconds());
        return verification.build(clock);
    }

    private String requireConfiguredSharedSecret() {
        if (!StringUtils.hasText(upstreamAuthProperties.getSharedSecret())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SOTD_UPSTREAM_AUTH_SHARED_SECRET is not configured."
            );
        }
        return upstreamAuthProperties.getSharedSecret();
    }

    private String requireConfiguredIssuer() {
        if (!StringUtils.hasText(upstreamAuthProperties.getIssuer())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SOTD_UPSTREAM_AUTH_ISSUER is not configured."
            );
        }
        return upstreamAuthProperties.getIssuer();
    }

    private String requireConfiguredAudience() {
        if (!StringUtils.hasText(upstreamAuthProperties.getAudience())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SOTD_UPSTREAM_AUTH_AUDIENCE is not configured."
            );
        }
        return upstreamAuthProperties.getAudience();
    }

    private static Instant requireTimestamp(Date value, String label) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Upstream authorization token is missing an " + label + ".");
        }
        return value.toInstant();
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

    public record VerifiedUpstreamRequest(
            UUID appUserId,
            Instant expiresAt
    ) {
    }
}
