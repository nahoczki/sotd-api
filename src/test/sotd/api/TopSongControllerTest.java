package sotd.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sotd.song.SongPeriodType;
import sotd.song.TopSongResponse;
import sotd.song.TopSongService;

class TopSongControllerTest {

    @Test
    void getTopSongDelegatesToService() {
        TopSongService topSongService = mock(TopSongService.class);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TopSongResponse expected = new TopSongResponse(
                "ready",
                "Result available",
                appUserId,
                "lukerykta",
                "Luke",
                SongPeriodType.MONTH,
                LocalDate.parse("2026-03-01"),
                "track-1",
                "Track Name",
                4,
                "rule"
        );
        when(topSongService.getTopSong(appUserId, SongPeriodType.MONTH)).thenReturn(expected);

        TopSongController controller = new TopSongController(topSongService);

        TopSongResponse actual = controller.getTopSong(appUserId, SongPeriodType.MONTH);

        assertThat(actual).isSameAs(expected);
        verify(topSongService).getTopSong(appUserId, SongPeriodType.MONTH);
    }
}
