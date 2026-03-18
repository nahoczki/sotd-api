package sotd.spotify.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

    /**
     * Returns the spotify user id and timezone for the most recently updated linked account.
     */
    public Optional<MostRecentSpotifyAccount> findMostRecentAccountIdentity() {
        return jdbcClient.sql("""
                select
                    spotify_user_id,
                    timezone
                from spotify_account
                order by updated_at desc
                limit 1
                """)
                .query((rs, rowNum) -> new MostRecentSpotifyAccount(
                        rs.getString("spotify_user_id"),
                        rs.getString("timezone")
                ))
                .optional();
    }

    /**
     * Returns the timezone for a specific linked Spotify user.
     */
    public Optional<String> findTimezoneBySpotifyUserId(String spotifyUserId) {
        return jdbcClient.sql("""
                select timezone
                from spotify_account
                where spotify_user_id = ?
                """)
                .param(spotifyUserId)
                .query(String.class)
                .optional();
    }

    /**
     * Returns active accounts that are eligible for background Spotify polling.
     */
    public List<SpotifyPollingAccount> findActiveAccountsForPolling() {
        return jdbcClient.sql("""
                select
                    id,
                    spotify_user_id,
                    refresh_token_encrypted,
                    access_token_expires_at,
                    last_recently_played_cursor_ms,
                    timezone
                from spotify_account
                where status = 'ACTIVE'
                order by updated_at desc
                """)
                .query((rs, rowNum) -> new SpotifyPollingAccount(
                        rs.getLong("id"),
                        rs.getString("spotify_user_id"),
                        rs.getBytes("refresh_token_encrypted"),
                        toInstant(rs.getTimestamp("access_token_expires_at")),
                        rs.getObject("last_recently_played_cursor_ms", Long.class),
                        rs.getString("timezone")
                ))
                .list();
    }

    /**
     * Persists new token metadata after a refresh-token exchange.
     */
    public void updateTokenState(
            long accountId,
            byte[] encryptedRefreshToken,
            Instant accessTokenExpiresAt,
            Instant lastRefreshAt
    ) {
        jdbcClient.sql("""
                update spotify_account
                set refresh_token_encrypted = ?,
                    access_token_expires_at = ?,
                    last_refresh_at = ?,
                    status = 'ACTIVE',
                    updated_at = current_timestamp
                where id = ?
                """)
                .params(
                        encryptedRefreshToken,
                        Timestamp.from(accessTokenExpiresAt),
                        Timestamp.from(lastRefreshAt),
                        accountId
                )
                .update();
    }

    /**
     * Marks the polling checkpoint after a successful recently-played sync.
     */
    public void updatePollingCheckpoint(long accountId, Instant lastSuccessfulPollAt, Long lastRecentlyPlayedCursorMs) {
        jdbcClient.sql("""
                update spotify_account
                set last_successful_poll_at = ?,
                    last_recently_played_cursor_ms = coalesce(?, last_recently_played_cursor_ms),
                    updated_at = current_timestamp
                where id = ?
                """)
                .params(
                        Timestamp.from(lastSuccessfulPollAt),
                        lastRecentlyPlayedCursorMs,
                        accountId
                )
                .update();
    }

    /**
     * Marks a linked account as requiring reauthorization.
     */
    public void markReauthRequired(long accountId) {
        jdbcClient.sql("""
                update spotify_account
                set status = 'REAUTH_REQUIRED',
                    updated_at = current_timestamp
                where id = ?
                """)
                .param(accountId)
                .update();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    public record MostRecentSpotifyAccount(
            String spotifyUserId,
            String timezone
    ) {
    }
}
