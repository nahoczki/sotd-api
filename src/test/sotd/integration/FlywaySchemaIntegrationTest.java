package sotd.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import sotd.support.PostgresJdbcIntegrationTestSupport;

class FlywaySchemaIntegrationTest extends PostgresJdbcIntegrationTestSupport {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void flywayAppliesAllExpectedMigrations() {
        List<String> versions = jdbcClient.sql("""
                select version
                from flyway_schema_history
                where success = true
                order by installed_rank
                """)
                .query(String.class)
                .list();

        assertThat(versions).containsExactly("1", "2", "3", "4");
    }

    @Test
    void coreTablesExistAfterMigration() {
        List<String> tables = jdbcClient.sql("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                      'spotify_account',
                      'spotify_track',
                      'spotify_artist',
                      'spotify_track_artist',
                      'playback_event',
                      'spotify_auth_state',
                      'song_period_rollup',
                      'song_period_winner'
                  )
                order by table_name
                """)
                .query(String.class)
                .list();

        assertThat(tables).containsExactly(
                "playback_event",
                "song_period_rollup",
                "song_period_winner",
                "spotify_account",
                "spotify_artist",
                "spotify_auth_state",
                "spotify_track",
                "spotify_track_artist"
        );
    }
}
