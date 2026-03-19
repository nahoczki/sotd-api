package sotd.spotify;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
/**
 * PostgreSQL-backed storage for OAuth state values during the Spotify connect flow.
 *
 * <p>Persisting state in PostgreSQL keeps the connect/callback handshake valid across restarts and
 * across pods that share the same database.
 */
public class SpotifyAuthStateStore {

    private static final int STATE_BYTES = 24;
    private static final int MAX_INSERT_ATTEMPTS = 3;

    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final RandomStateGenerator randomStateGenerator;

    @Autowired
    public SpotifyAuthStateStore(JdbcClient jdbcClient, Clock clock) {
        this(jdbcClient, clock, new RandomStateGenerator());
    }

    SpotifyAuthStateStore(JdbcClient jdbcClient, Clock clock, RandomStateGenerator randomStateGenerator) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
        this.randomStateGenerator = randomStateGenerator;
    }

    /**
     * Issues a one-time state token that expires at the supplied timestamp.
     */
    @Transactional
    public String issueState(UUID appUserId, Instant expiresAt) {
        Objects.requireNonNull(appUserId, "appUserId");
        Objects.requireNonNull(expiresAt, "expiresAt");

        evictExpired(clock.instant());

        for (int attempt = 0; attempt < MAX_INSERT_ATTEMPTS; attempt++) {
            String state = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(randomStateGenerator.generate(STATE_BYTES));
            int inserted = jdbcClient.sql("""
                    insert into spotify_auth_state (
                        state_token,
                        app_user_id,
                        expires_at
                    ) values (?, ?, ?)
                    on conflict do nothing
                    """)
                    .params(state, appUserId, Timestamp.from(expiresAt))
                    .update();
            if (inserted == 1) {
                return state;
            }
        }

        throw new IllegalStateException("Failed to issue a unique Spotify auth state token.");
    }

    /**
     * Consumes a state token once and returns whether it was valid and unexpired.
     */
    @Transactional
    public Optional<UUID> consume(String state) {
        if (state == null) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        evictExpired(now);
        return jdbcClient.sql("""
                delete from spotify_auth_state
                where state_token = ?
                  and expires_at > ?
                returning app_user_id
                """)
                .params(state, Timestamp.from(now))
                .query((rs, rowNum) -> rs.getObject("app_user_id", UUID.class))
                .optional();
    }

    private void evictExpired(Instant now) {
        jdbcClient.sql("""
                delete from spotify_auth_state
                where expires_at <= ?
                """)
                .param(Timestamp.from(now))
                .update();
    }
}
