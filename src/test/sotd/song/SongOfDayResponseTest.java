package sotd.song;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SongOfDayResponseTest {

    @Test
    void unavailableReturnsPendingPlaceholderResponse() {
        SongOfDayResponse response = SongOfDayResponse.unavailable();

        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.message()).isEqualTo("No song-of-the-day data has been computed yet.");
        assertThat(response.spotifyUserId()).isNull();
        assertThat(response.trackName()).isNull();
    }
}
