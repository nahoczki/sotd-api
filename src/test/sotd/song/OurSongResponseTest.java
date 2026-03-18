package sotd.song;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OurSongResponseTest {

    @Test
    void unlinkedReturnsScopedPlaceholderResponse() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        OurSongResponse response = OurSongResponse.unlinked(
                appUserId,
                otherUserId,
                OurSongPeriodType.DAY,
                LocalDate.parse("2026-03-17"),
                "No Spotify account is linked for the comparison user."
        );

        assertThat(response.status()).isEqualTo("unlinked");
        assertThat(response.message()).isEqualTo("No Spotify account is linked for the comparison user.");
        assertThat(response.appUserId()).isEqualTo(appUserId);
        assertThat(response.otherUserId()).isEqualTo(otherUserId);
        assertThat(response.trackName()).isNull();
        assertThat(response.combinedPlayCount()).isNull();
    }

    @Test
    void noCommonSongReturnsExpectedPlaceholderResponse() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        OurSongResponse response = OurSongResponse.noCommonSong(
                appUserId,
                otherUserId,
                OurSongPeriodType.WEEK,
                LocalDate.parse("2026-03-16")
        );

        assertThat(response.status()).isEqualTo("no-common-song");
        assertThat(response.message()).isEqualTo("No common song was found for the requested period.");
        assertThat(response.periodType()).isEqualTo(OurSongPeriodType.WEEK);
        assertThat(response.periodStartLocal()).isEqualTo(LocalDate.parse("2026-03-16"));
        assertThat(response.spotifyTrackId()).isNull();
    }

    @Test
    void availableMapsSharedSongView() {
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        OurSongMatchView match = new OurSongMatchView(
                appUserId,
                otherUserId,
                OurSongPeriodType.DAY,
                LocalDate.parse("2026-03-17"),
                "track-1",
                "Track Name",
                3,
                5,
                8,
                OurSongRepository.TIE_BREAK_RULE
        );

        OurSongResponse response = OurSongResponse.available(match);

        assertThat(response.status()).isEqualTo("ready");
        assertThat(response.trackName()).isEqualTo("Track Name");
        assertThat(response.userPlayCount()).isEqualTo(3);
        assertThat(response.otherUserPlayCount()).isEqualTo(5);
        assertThat(response.combinedPlayCount()).isEqualTo(8);
        assertThat(response.tieBreakRule()).isEqualTo(OurSongRepository.TIE_BREAK_RULE);
    }
}
