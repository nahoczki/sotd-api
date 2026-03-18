package sotd.spotify;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
/**
 * In-memory storage for OAuth state values during the Spotify connect flow.
 *
 * <p>This is intentionally simple for the current single-instance POC. It must move to a shared store
 * before multi-instance deployment.
 */
public class SpotifyAuthStateStore {

    private static final int STATE_BYTES = 24;

    private final Clock clock;
    private final RandomStateGenerator randomStateGenerator;
    private final Map<String, Instant> stateExpirations = new ConcurrentHashMap<>();

    @Autowired
    public SpotifyAuthStateStore(Clock clock) {
        this(clock, new RandomStateGenerator());
    }

    SpotifyAuthStateStore(Clock clock, RandomStateGenerator randomStateGenerator) {
        this.clock = clock;
        this.randomStateGenerator = randomStateGenerator;
    }

    /**
     * Issues a one-time state token that expires at the supplied timestamp.
     */
    public String issueState(Instant expiresAt) {
        evictExpired(clock.instant());
        String state = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomStateGenerator.generate(STATE_BYTES));
        stateExpirations.put(state, expiresAt);
        return state;
    }

    /**
     * Consumes a state token once and returns whether it was valid and unexpired.
     */
    public boolean consume(String state) {
        if (state == null) {
            return false;
        }

        Instant now = clock.instant();
        evictExpired(now);
        Instant expiresAt = stateExpirations.remove(state);
        return Optional.ofNullable(expiresAt)
                .map(expiry -> expiry.isAfter(now))
                .orElse(false);
    }

    private void evictExpired(Instant now) {
        stateExpirations.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }
}
