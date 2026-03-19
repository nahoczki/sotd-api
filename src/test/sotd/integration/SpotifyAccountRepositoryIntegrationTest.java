package sotd.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import sotd.spotify.SpotifyLinkedAccountView;
import sotd.spotify.client.SpotifyCurrentUserProfile;
import sotd.spotify.persistence.SpotifyAccountRepository;
import sotd.support.PostgresJdbcIntegrationTestSupport;

@Transactional
class SpotifyAccountRepositoryIntegrationTest extends PostgresJdbcIntegrationTestSupport {

    @Autowired
    private SpotifyAccountRepository spotifyAccountRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void saveOrUpdatePersistsLinkedAccountAndPollingState() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant tokenExpiresAt = Instant.parse("2026-03-19T00:00:00Z");
        Instant lastRefreshAt = Instant.parse("2026-03-18T23:00:00Z");

        spotifyAccountRepository.saveOrUpdate(
                appUserId,
                new SpotifyCurrentUserProfile("lukerykta", "Luke"),
                new byte[]{1, 2, 3},
                "user-read-recently-played",
                tokenExpiresAt,
                lastRefreshAt,
                "America/Chicago",
                12345L
        );

        SpotifyLinkedAccountView linkedAccount = spotifyAccountRepository.findByAppUserId(appUserId).orElseThrow();

        assertThat(linkedAccount.appUserId()).isEqualTo(appUserId);
        assertThat(linkedAccount.spotifyUserId()).isEqualTo("lukerykta");
        assertThat(linkedAccount.displayName()).isEqualTo("Luke");
        assertThat(linkedAccount.scope()).isEqualTo("user-read-recently-played");
        assertThat(linkedAccount.status()).isEqualTo("ACTIVE");
        assertThat(linkedAccount.timezone()).isEqualTo("America/Chicago");

        Boolean cursorIsNull = jdbcClient.sql("""
                select last_recently_played_cursor_ms is null
                from spotify_account
                where app_user_id = ?
                """)
                .param(appUserId)
                .query(Boolean.class)
                .single();

        assertThat(cursorIsNull).isTrue();
        assertThat(spotifyAccountRepository.findActiveAccountsForPolling()).hasSize(1);
    }

    @Test
    void disconnectByAppUserIdSoftDisconnectsTheExistingRow() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        spotifyAccountRepository.saveOrUpdate(
                appUserId,
                new SpotifyCurrentUserProfile("lukerykta", "Luke"),
                new byte[]{1, 2, 3},
                "user-read-recently-played",
                Instant.parse("2026-03-19T00:00:00Z"),
                Instant.parse("2026-03-18T23:00:00Z"),
                "America/Chicago",
                12345L
        );

        SpotifyAccountRepository.DisconnectedSpotifyAccount disconnected = spotifyAccountRepository.disconnectByAppUserId(appUserId)
                .orElseThrow();

        assertThat(disconnected.spotifyUserId()).isEqualTo("lukerykta");
        assertThat(spotifyAccountRepository.findByAppUserId(appUserId)).isEmpty();
        assertThat(spotifyAccountRepository.findActiveAccountsForPolling()).isEmpty();

        AccountRow accountRow = jdbcClient.sql("""
                select app_user_id, status, refresh_token_encrypted is not null as has_refresh_token
                from spotify_account
                where id = ?
                """)
                .param(disconnected.accountId())
                .query((rs, rowNum) -> new AccountRow(
                        rs.getObject("app_user_id", UUID.class),
                        rs.getString("status"),
                        rs.getBoolean("has_refresh_token")
                ))
                .single();

        assertThat(accountRow.appUserId()).isNull();
        assertThat(accountRow.status()).isEqualTo("DISCONNECTED");
        assertThat(accountRow.hasRefreshToken()).isFalse();
    }

    @Test
    void relinkingAfterDisconnectReusesTheRowAndResetsTheCursor() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        spotifyAccountRepository.saveOrUpdate(
                appUserId,
                new SpotifyCurrentUserProfile("lukerykta", "Luke"),
                new byte[]{1, 2, 3},
                "scope-a",
                Instant.parse("2026-03-19T00:00:00Z"),
                Instant.parse("2026-03-18T23:00:00Z"),
                "America/Chicago",
                1000L
        );

        long originalId = jdbcClient.sql("""
                select id
                from spotify_account
                where spotify_user_id = 'lukerykta'
                """)
                .query(Long.class)
                .single();

        spotifyAccountRepository.disconnectByAppUserId(appUserId);
        spotifyAccountRepository.saveOrUpdate(
                appUserId,
                new SpotifyCurrentUserProfile("lukerykta", "Luke"),
                new byte[]{4, 5, 6},
                "scope-b",
                Instant.parse("2026-03-20T00:00:00Z"),
                Instant.parse("2026-03-19T23:00:00Z"),
                "America/Chicago",
                99999L
        );

        RelinkedAccountRow accountRow = jdbcClient.sql("""
                select id, status, last_recently_played_cursor_ms
                from spotify_account
                where spotify_user_id = 'lukerykta'
                """)
                .query((rs, rowNum) -> new RelinkedAccountRow(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getLong("last_recently_played_cursor_ms")
                ))
                .single();

        assertThat(accountRow.id()).isEqualTo(originalId);
        assertThat(accountRow.status()).isEqualTo("ACTIVE");
        assertThat(accountRow.lastRecentlyPlayedCursorMs()).isEqualTo(99999L);
    }

    private record AccountRow(
            UUID appUserId,
            String status,
            boolean hasRefreshToken
    ) {
    }

    private record RelinkedAccountRow(
            long id,
            String status,
            long lastRecentlyPlayedCursorMs
    ) {
    }
}
