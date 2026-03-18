package sotd.spotify.persistence;

import java.time.Instant;

/**
 * Internal view of a linked Spotify account used by the background polling flow.
 */
public record SpotifyPollingAccount(
        long id,
        String spotifyUserId,
        byte[] refreshTokenEncrypted,
        Instant accessTokenExpiresAt,
        Long lastRecentlyPlayedCursorMs,
        String timezone
) {
}
