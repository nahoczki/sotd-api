package sotd.song;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Queries the current daily winner for a linked Spotify account.
 */
@Repository
public class SongOfDayRepository {

    private final JdbcClient jdbcClient;

    public SongOfDayRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<SongOfDayWinnerView> findCurrentWinner(String spotifyUserId, LocalDate localDate) {
        return jdbcClient.sql("""
                select
                    sa.spotify_user_id,
                    sa.display_name,
                    sa.timezone,
                    spw.period_start_local,
                    spw.spotify_track_id,
                    st.name as track_name,
                    spw.play_count
                from spotify_account sa
                join song_period_winner spw on spw.spotify_account_id = sa.id
                join spotify_track st on st.spotify_track_id = spw.spotify_track_id
                where sa.spotify_user_id = ?
                  and spw.period_type = 'DAY'
                  and spw.period_start_local = ?
                limit 1
                """)
                .params(spotifyUserId, localDate)
                .query((rs, rowNum) -> new SongOfDayWinnerView(
                        rs.getString("spotify_user_id"),
                        rs.getString("display_name"),
                        rs.getString("timezone"),
                        rs.getObject("period_start_local", LocalDate.class),
                        rs.getString("spotify_track_id"),
                        rs.getString("track_name"),
                        rs.getInt("play_count")
                ))
                .optional();
    }
}
