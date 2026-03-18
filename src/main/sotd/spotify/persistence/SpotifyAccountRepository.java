package sotd.spotify.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sotd.spotify.SpotifyLinkedAccountView;
import sotd.spotify.client.SpotifyCurrentUserProfile;

@Repository
/**
 * JDBC persistence for linked Spotify accounts.
 *
 * <p>The current repository shape assumes a private single-user app and exposes a "most recent account"
 * lookup for the `/api/spotify/connection` endpoint.
 */
public class SpotifyAccountRepository {

    private final JdbcClient jdbcClient;

    public SpotifyAccountRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Upserts the linked Spotify account after a successful OAuth callback.
     */
    public void saveOrUpdate(
            SpotifyCurrentUserProfile userProfile,
            byte[] encryptedRefreshToken,
            String scope,
            Instant accessTokenExpiresAt,
            Instant lastRefreshAt,
            String timezone
    ) {
        jdbcClient.sql("""
                insert into spotify_account (
                    spotify_user_id,
                    display_name,
                    scope,
                    refresh_token_encrypted,
                    access_token_expires_at,
                    last_refresh_at,
                    timezone,
                    status,
                    updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', current_timestamp)
                on conflict (spotify_user_id) do update
                set display_name = excluded.display_name,
                    scope = excluded.scope,
                    refresh_token_encrypted = excluded.refresh_token_encrypted,
                    access_token_expires_at = excluded.access_token_expires_at,
                    last_refresh_at = excluded.last_refresh_at,
                    timezone = excluded.timezone,
                    status = 'ACTIVE',
                    updated_at = current_timestamp
                """)
                .params(
                        userProfile.id(),
                        userProfile.displayName(),
                        scope,
                        encryptedRefreshToken,
                        Timestamp.from(accessTokenExpiresAt),
                        Timestamp.from(lastRefreshAt),
                        timezone
                )
                .update();
    }

    /**
     * Returns the most recently updated linked account row for the current single-user POC.
     */
    public Optional<SpotifyLinkedAccountView> findMostRecent() {
        return jdbcClient.sql("""
                select
                    spotify_user_id,
                    display_name,
                    scope,
                    status,
                    timezone,
                    access_token_expires_at,
                    created_at,
                    updated_at
                from spotify_account
                order by updated_at desc
                limit 1
                """)
                .query((rs, rowNum) -> new SpotifyLinkedAccountView(
                        rs.getString("spotify_user_id"),
                        rs.getString("display_name"),
                        rs.getString("scope"),
                        rs.getString("status"),
                        rs.getString("timezone"),
                        toInstant(rs.getTimestamp("access_token_expires_at")),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("updated_at"))
                ))
                .optional();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
