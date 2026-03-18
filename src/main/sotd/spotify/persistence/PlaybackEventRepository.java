package sotd.spotify.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persists normalized playback history events.
 */
@Repository
public class PlaybackEventRepository {

    private final JdbcClient jdbcClient;

    public PlaybackEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean insertIfAbsent(
            long spotifyAccountId,
            String spotifyTrackId,
            Instant playedAtUtc,
            LocalDate playedDateLocal,
            String sourceContextType,
            String sourceContextUri,
            String rawPayloadJson
    ) {
        int rowsUpdated = jdbcClient.sql("""
                insert into playback_event (
                    spotify_account_id,
                    spotify_track_id,
                    played_at_utc,
                    played_date_local,
                    source_context_type,
                    source_context_uri,
                    raw_payload_json
                ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (spotify_account_id, spotify_track_id, played_at_utc) do nothing
                """)
                .params(
                        spotifyAccountId,
                        spotifyTrackId,
                        Timestamp.from(playedAtUtc),
                        playedDateLocal,
                        sourceContextType,
                        sourceContextUri,
                        rawPayloadJson
                )
                .update();
        return rowsUpdated > 0;
    }
}
