package sotd.song;

import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Rebuilds song rollups and winner rows from normalized playback events.
 */
@Repository
public class SongRollupRepository {

    private static final String DAY = "DAY";
    private static final String TIE_BREAK_RULE = "PLAY_COUNT_THEN_LAST_PLAYED_THEN_TRACK_ID";

    private final JdbcClient jdbcClient;

    public SongRollupRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void rebuildDay(long spotifyAccountId, LocalDate periodStartLocal) {
        jdbcClient.sql("""
                delete from song_period_rollup
                where spotify_account_id = ?
                  and period_type = ?
                  and period_start_local = ?
                """)
                .params(spotifyAccountId, DAY, periodStartLocal)
                .update();

        jdbcClient.sql("""
                insert into song_period_rollup (
                    spotify_account_id,
                    period_type,
                    period_start_local,
                    spotify_track_id,
                    play_count,
                    total_duration_ms,
                    last_played_at_utc
                )
                select
                    pe.spotify_account_id,
                    ?,
                    pe.played_date_local,
                    pe.spotify_track_id,
                    count(*) as play_count,
                    sum(st.duration_ms) as total_duration_ms,
                    max(pe.played_at_utc) as last_played_at_utc
                from playback_event pe
                join spotify_track st on st.spotify_track_id = pe.spotify_track_id
                where pe.spotify_account_id = ?
                  and pe.played_date_local = ?
                group by pe.spotify_account_id, pe.played_date_local, pe.spotify_track_id
                """)
                .params(DAY, spotifyAccountId, periodStartLocal)
                .update();

        jdbcClient.sql("""
                delete from song_period_winner
                where spotify_account_id = ?
                  and period_type = ?
                  and period_start_local = ?
                """)
                .params(spotifyAccountId, DAY, periodStartLocal)
                .update();

        jdbcClient.sql("""
                insert into song_period_winner (
                    spotify_account_id,
                    period_type,
                    period_start_local,
                    spotify_track_id,
                    play_count,
                    tie_break_rule
                )
                select
                    spotify_account_id,
                    period_type,
                    period_start_local,
                    spotify_track_id,
                    play_count,
                    ?
                from song_period_rollup
                where spotify_account_id = ?
                  and period_type = ?
                  and period_start_local = ?
                order by play_count desc, last_played_at_utc desc, spotify_track_id asc
                limit 1
                """)
                .params(TIE_BREAK_RULE, spotifyAccountId, DAY, periodStartLocal)
                .update();
    }
}
