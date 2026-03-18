package sotd.song;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Queries the highest-ranked common song for two users by aggregating existing daily rollups.
 */
@Repository
public class OurSongRepository {

    static final String TIE_BREAK_RULE =
            "LEAST_SHARED_COUNT_THEN_COMBINED_PLAY_COUNT_THEN_DURATION_THEN_LAST_PLAYED_THEN_TRACK_ID";

    private final JdbcClient jdbcClient;

    public OurSongRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<OurSongMatchView> findBestSharedSong(
            UUID appUserId,
            UUID otherUserId,
            SongPeriodType periodType,
            LocalDate periodStartLocal,
            LocalDate periodEndExclusive
    ) {
        return jdbcClient.sql("""
                with first_account as (
                    select id
                    from spotify_account
                    where app_user_id = ?
                ),
                second_account as (
                    select id
                    from spotify_account
                    where app_user_id = ?
                ),
                first_rollup as (
                    select
                        spotify_track_id,
                        sum(play_count) as play_count,
                        sum(total_duration_ms) as total_duration_ms,
                        max(last_played_at_utc) as last_played_at_utc
                    from song_period_rollup
                    where spotify_account_id = (select id from first_account)
                      and period_type = 'DAY'
                      and period_start_local >= ?
                      and period_start_local < ?
                    group by spotify_track_id
                ),
                second_rollup as (
                    select
                        spotify_track_id,
                        sum(play_count) as play_count,
                        sum(total_duration_ms) as total_duration_ms,
                        max(last_played_at_utc) as last_played_at_utc
                    from song_period_rollup
                    where spotify_account_id = (select id from second_account)
                      and period_type = 'DAY'
                      and period_start_local >= ?
                      and period_start_local < ?
                    group by spotify_track_id
                )
                select
                    fr.spotify_track_id,
                    st.name as track_name,
                    fr.play_count as user_play_count,
                    sr.play_count as other_user_play_count,
                    fr.play_count + sr.play_count as combined_play_count
                from first_rollup fr
                join second_rollup sr on sr.spotify_track_id = fr.spotify_track_id
                join spotify_track st on st.spotify_track_id = fr.spotify_track_id
                order by
                    least(fr.play_count, sr.play_count) desc,
                    fr.play_count + sr.play_count desc,
                    fr.total_duration_ms + sr.total_duration_ms desc,
                    greatest(fr.last_played_at_utc, sr.last_played_at_utc) desc,
                    fr.spotify_track_id asc
                limit 1
                """)
                .params(
                        appUserId,
                        otherUserId,
                        periodStartLocal,
                        periodEndExclusive,
                        periodStartLocal,
                        periodEndExclusive
                )
                .query((rs, rowNum) -> new OurSongMatchView(
                        appUserId,
                        otherUserId,
                        periodType,
                        periodStartLocal,
                        rs.getString("spotify_track_id"),
                        rs.getString("track_name"),
                        rs.getInt("user_play_count"),
                        rs.getInt("other_user_play_count"),
                        rs.getInt("combined_play_count"),
                        TIE_BREAK_RULE
                ))
                .optional();
    }
}
