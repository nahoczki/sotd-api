package sotd.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sotd.song.OurSongPeriodType;
import sotd.song.OurSongResponse;
import sotd.song.OurSongService;

class OurSongControllerTest {

    @Test
    void getOurSongDelegatesToService() {
        OurSongService ourSongService = mock(OurSongService.class);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        OurSongResponse expected = new OurSongResponse(
                "ready",
                "Shared-song data is available.",
                appUserId,
                otherUserId,
                OurSongPeriodType.DAY,
                LocalDate.parse("2026-03-17"),
                "track-1",
                "Track Name",
                3,
                5,
                8,
                "rule"
        );
        when(ourSongService.getCurrentSharedSong(appUserId, otherUserId, OurSongPeriodType.DAY)).thenReturn(expected);

        OurSongController controller = new OurSongController(ourSongService);

        OurSongResponse actual = controller.getOurSong(appUserId, otherUserId, OurSongPeriodType.DAY);

        assertThat(actual).isSameAs(expected);
        verify(ourSongService).getCurrentSharedSong(appUserId, otherUserId, OurSongPeriodType.DAY);
    }
}
