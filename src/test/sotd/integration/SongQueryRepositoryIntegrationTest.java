package sotd.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import sotd.song.OurSongMatchView;
import sotd.song.OurSongRepository;
import sotd.song.SongPeriodType;
import sotd.song.TopSongRepository;
import sotd.song.TopSongWinnerView;
import sotd.support.PostgresJdbcIntegrationTestSupport;

@Transactional
class SongQueryRepositoryIntegrationTest extends PostgresJdbcIntegrationTestSupport {

    @Autowired
    private TopSongRepository topSongRepository;

    @Autowired
    private OurSongRepository ourSongRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void findCurrentWinnerReturnsTheStoredDailyWinner() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        long accountId = insertSpotifyAccount(appUserId, "lukerykta", "Luke");
        insertSpotifyTrack("track-1", "Track One");
        insertSongPeriodRollup(accountId, "track-1", LocalDate.parse("2026-03-18"), 4, 720_000L, "2026-03-18T21:00:00Z");

        TopSongWinnerView winner = topSongRepository.findTopSong(
                        appUserId,
                        SongPeriodType.DAY,
                        LocalDate.parse("2026-03-18"),
                        LocalDate.parse("2026-03-19")
                )
                .orElseThrow();

        assertThat(winner.appUserId()).isEqualTo(appUserId);
        assertThat(winner.spotifyUserId()).isEqualTo("lukerykta");
        assertThat(winner.trackName()).isEqualTo("Track One");
        assertThat(winner.playCount()).isEqualTo(4);
    }

    @Test
    void findBestSharedSongPrefersTheHigherMutualPlayCountOverTheHigherRawTotal() {
        UUID firstUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID secondUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        long firstAccountId = insertSpotifyAccount(firstUserId, "lukerykta", "Luke");
        long secondAccountId = insertSpotifyAccount(secondUserId, "partner", "Partner");

        insertSpotifyTrack("track-one-sided", "One Sided Song");
        insertSpotifyTrack("track-balanced", "Balanced Song");

        LocalDate day = LocalDate.parse("2026-03-18");

        insertSongPeriodRollup(firstAccountId, "track-one-sided", day, 10, 10_000L, "2026-03-18T20:00:00Z");
        insertSongPeriodRollup(secondAccountId, "track-one-sided", day, 1, 1_000L, "2026-03-18T19:00:00Z");

        insertSongPeriodRollup(firstAccountId, "track-balanced", day, 3, 3_000L, "2026-03-18T18:00:00Z");
        insertSongPeriodRollup(secondAccountId, "track-balanced", day, 2, 2_000L, "2026-03-18T17:00:00Z");

        OurSongMatchView match = ourSongRepository.findBestSharedSong(
                        firstUserId,
                        secondUserId,
                        SongPeriodType.DAY,
                        day,
                        day.plusDays(1)
                )
                .orElseThrow();

        assertThat(match.spotifyTrackId()).isEqualTo("track-balanced");
        assertThat(match.trackName()).isEqualTo("Balanced Song");
        assertThat(match.userPlayCount()).isEqualTo(3);
        assertThat(match.otherUserPlayCount()).isEqualTo(2);
        assertThat(match.combinedPlayCount()).isEqualTo(5);
        assertThat(match.tieBreakRule())
                .isEqualTo("LEAST_SHARED_COUNT_THEN_COMBINED_PLAY_COUNT_THEN_DURATION_THEN_LAST_PLAYED_THEN_TRACK_ID");
    }

    private long insertSpotifyAccount(UUID appUserId, String spotifyUserId, String displayName) {
        return jdbcClient.sql("""
                insert into spotify_account (
                    app_user_id,
                    spotify_user_id,
                    display_name,
                    scope,
                    refresh_token_encrypted,
                    timezone,
                    status
                ) values (?, ?, ?, '', cast(E'\\\\x01' as bytea), 'America/Chicago', 'ACTIVE')
                returning id
                """)
                .params(appUserId, spotifyUserId, displayName)
                .query(Long.class)
                .single();
    }

    private void insertSpotifyTrack(String spotifyTrackId, String name) {
        jdbcClient.sql("""
                insert into spotify_track (
                    spotify_track_id,
                    name,
                    duration_ms
                ) values (?, ?, 180000)
                """)
                .params(spotifyTrackId, name)
                .update();
    }

    private void insertSongPeriodRollup(
            long accountId,
            String spotifyTrackId,
            LocalDate periodStartLocal,
            int playCount,
            long totalDurationMs,
            String lastPlayedAtUtc
    ) {
        jdbcClient.sql("""
                insert into song_period_rollup (
                    spotify_account_id,
                    period_type,
                    period_start_local,
                    spotify_track_id,
                    play_count,
                    total_duration_ms,
                    last_played_at_utc
                ) values (?, 'DAY', ?, ?, ?, ?, ?::timestamptz)
                """)
                .params(accountId, periodStartLocal, spotifyTrackId, playCount, totalDurationMs, lastPlayedAtUtc)
                .update();
    }
}
