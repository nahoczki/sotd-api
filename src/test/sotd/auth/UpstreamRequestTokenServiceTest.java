package sotd.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class UpstreamRequestTokenServiceTest {

    @Test
    void createTokenAndVerifyRoundTripForMatchingUser() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-19T00:00:00Z"), ZoneOffset.UTC);
        UpstreamRequestTokenService service = new UpstreamRequestTokenService(configuredProperties(), clock);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        String token = service.createToken(appUserId, Instant.parse("2026-03-19T00:05:00Z"));
        UpstreamRequestTokenService.VerifiedUpstreamRequest verified = service.verify(token);

        assertThat(verified.appUserId()).isEqualTo(appUserId);
        assertThat(verified.expiresAt()).isEqualTo(Instant.parse("2026-03-19T00:05:00Z"));
    }

    @Test
    void verifyRejectsExpiredTokenOutsideClockSkew() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-19T00:06:00Z"), ZoneOffset.UTC);
        UpstreamRequestTokenService service = new UpstreamRequestTokenService(configuredProperties(), clock);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String token = service.createToken(appUserId, Instant.parse("2026-03-19T00:05:00Z"));

        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("invalid");
    }

    @Test
    void verifyRejectsTamperedTokenSignature() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-19T00:00:00Z"), ZoneOffset.UTC);
        UpstreamRequestTokenService service = new UpstreamRequestTokenService(configuredProperties(), clock);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String token = service.createToken(appUserId, Instant.parse("2026-03-19T00:05:00Z"));
        String[] tokenParts = token.split("\\.");
        String tamperedToken = tokenParts[0] + "." + tokenParts[1] + ".invalid-signature";

        assertThatThrownBy(() -> service.verify(tamperedToken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("invalid");
    }

    @Test
    void verifyRejectsWrongAudience() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-19T00:00:00Z"), ZoneOffset.UTC);
        UpstreamAuthProperties issuingProperties = configuredProperties();
        UpstreamRequestTokenService issuingService = new UpstreamRequestTokenService(issuingProperties, clock);
        String token = issuingService.createToken(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Instant.parse("2026-03-19T00:05:00Z")
        );

        UpstreamAuthProperties verifyingProperties = configuredProperties();
        verifyingProperties.setAudience("different-audience");
        UpstreamRequestTokenService verifyingService = new UpstreamRequestTokenService(verifyingProperties, clock);

        assertThatThrownBy(() -> verifyingService.verify(token))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("invalid");
    }

    private static UpstreamAuthProperties configuredProperties() {
        UpstreamAuthProperties properties = new UpstreamAuthProperties();
        properties.setEnabled(true);
        properties.setSharedSecret("shared-secret-for-tests");
        properties.setIssuer("accounts-api");
        properties.setAudience("sotd-api");
        properties.setClockSkew(Duration.ofSeconds(30));
        return properties;
    }
}
