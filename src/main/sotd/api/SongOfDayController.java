package sotd.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sotd.song.SongOfDayResponse;
import sotd.song.SongOfDayService;

@RestController
@RequestMapping("/api")
public class SongOfDayController {

    private final SongOfDayService songOfDayService;

    public SongOfDayController(SongOfDayService songOfDayService) {
        this.songOfDayService = songOfDayService;
    }

    @GetMapping("/song-of-the-day")
    @ResponseStatus(HttpStatus.OK)
    public SongOfDayResponse getSongOfTheDay() {
        return songOfDayService.getCurrentSongOfDay();
    }
}
