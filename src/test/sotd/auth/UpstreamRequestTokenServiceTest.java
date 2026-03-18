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
                .hasMessageContaining("expired");
    }

    @Test
    void verifyRejectsTamperedTokenSignature() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-19T00:00:00Z"), ZoneOffset.UTC);
        UpstreamRequestTokenService service = new UpstreamRequestTokenService(configuredProperties(), clock);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String token = service.createToken(appUserId, Instant.parse("2026-03-19T00:05:00Z"));
        String tamperedToken = token.substring(0, token.length() - 1) + "A";

        assertThatThrownBy(() -> service.verify(tamperedToken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("signature");
    }

    private static UpstreamAuthProperties configuredProperties() {
        UpstreamAuthProperties properties = new UpstreamAuthProperties();
        properties.setEnabled(true);
        properties.setSharedSecret("shared-secret-for-tests");
        properties.setClockSkew(Duration.ofSeconds(30));
        return properties;
    }
}
