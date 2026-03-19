package sotd.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import sotd.spotify.SpotifyAuthStateStore;
import sotd.support.PostgresJdbcIntegrationTestSupport;

@Transactional
class SpotifyAuthStateStoreIntegrationTest extends PostgresJdbcIntegrationTestSupport {

    @Autowired
    private SpotifyAuthStateStore spotifyAuthStateStore;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void consumeReturnsTheUserForFreshStateAndRemovesItAfterUse() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        String state = spotifyAuthStateStore.issueState(appUserId, Instant.now().plusSeconds(300));

        assertThat(state).isNotBlank();
        assertThat(spotifyAuthStateStore.consume(state)).contains(appUserId);
        assertThat(spotifyAuthStateStore.consume(state)).isEmpty();
    }

    @Test
    void consumeReturnsEmptyForExpiredState() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String state = spotifyAuthStateStore.issueState(appUserId, Instant.now().minusSeconds(60));

        assertThat(spotifyAuthStateStore.consume(state)).isEmpty();
        assertThat(countStoredStates()).isZero();
    }

    @Test
    void issuingStateEvictsExpiredRows() {
        Instant now = Instant.now();

        jdbcClient.sql("""
                insert into spotify_auth_state (
                    state_token,
                    app_user_id,
                    expires_at
                ) values (?, ?, ?)
                """)
                .params(
                        "expired-state",
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        java.sql.Timestamp.from(now.minusSeconds(60))
                )
                .update();

        spotifyAuthStateStore.issueState(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                now.plusSeconds(300)
        );

        assertThat(countStoredStates()).isEqualTo(1);
    }

    private long countStoredStates() {
        return jdbcClient.sql("""
                select count(*)
                from spotify_auth_state
                """)
                .query(Long.class)
                .single();
    }
}
