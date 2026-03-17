package sotd.song;

import org.springframework.stereotype.Service;

@Service
public class SongOfDayService {

    public SongOfDayResponse getCurrentSongOfDay() {
        return SongOfDayResponse.unavailable();
    }
}
