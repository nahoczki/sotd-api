package sotd.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import sotd.song.SongOfDayResponse;
import sotd.song.SongOfDayService;

class SongOfDayControllerTest {

    @Test
    void getSongOfTheDayDelegatesToService() {
        SongOfDayService songOfDayService = mock(SongOfDayService.class);
        SongOfDayResponse expected = new SongOfDayResponse(
                "ready",
                "Result available",
                "lukerykta",
                "Luke",
                null,
                "track-1",
                "Track Name",
                4
        );
        when(songOfDayService.getCurrentSongOfDay("lukerykta")).thenReturn(expected);

        SongOfDayController controller = new SongOfDayController(songOfDayService);

        SongOfDayResponse actual = controller.getSongOfTheDay("lukerykta");

        assertThat(actual).isSameAs(expected);
        verify(songOfDayService).getCurrentSongOfDay("lukerykta");
    }
}
