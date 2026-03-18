package sotd.song;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sotd.spotify.persistence.SpotifyAccountRepository;
import sotd.spotify.persistence.SpotifyAccountRepository.MostRecentSpotifyAccount;

class SongOfDayServiceTest {

    @Test
    void getCurrentSongOfDayReturnsCurrentWinnerForMostRecentAccount() {
        SongOfDayRepository songOfDayRepository = mock(SongOfDayRepository.class);
        SpotifyAccountRepository spotifyAccountRepository = mock(SpotifyAccountRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T03:00:00Z"), ZoneOffset.UTC);

        when(spotifyAccountRepository.findMostRecentAccountIdentity())
                .thenReturn(Optional.of(new MostRecentSpotifyAccount("lukerykta", "America/New_York")));
        when(songOfDayRepository.findCurrentWinner("lukerykta", LocalDate.parse("2026-03-17")))
                .thenReturn(Optional.of(new SongOfDayWinnerView(
                        "lukerykta",
                        "Luke",
                        "America/New_York",
                        LocalDate.parse("2026-03-17"),
                        "track-1",
                        "Track Name",
                        4
                )));

        SongOfDayService service = new SongOfDayService(songOfDayRepository, spotifyAccountRepository, clock);

        SongOfDayResponse response = service.getCurrentSongOfDay(null);

        assertThat(response.status()).isEqualTo("ready");
        assertThat(response.spotifyUserId()).isEqualTo("lukerykta");
        assertThat(response.trackName()).isEqualTo("Track Name");
        assertThat(response.playCount()).isEqualTo(4);
    }

    @Test
    void getCurrentSongOfDaySupportsExplicitSpotifyUserId() {
        SongOfDayRepository songOfDayRepository = mock(SongOfDayRepository.class);
        SpotifyAccountRepository spotifyAccountRepository = mock(SpotifyAccountRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T12:00:00Z"), ZoneOffset.UTC);

        when(spotifyAccountRepository.findTimezoneBySpotifyUserId("partner"))
                .thenReturn(Optional.of("UTC"));
        when(songOfDayRepository.findCurrentWinner("partner", LocalDate.parse("2026-03-18")))
                .thenReturn(Optional.of(new SongOfDayWinnerView(
                        "partner",
                        "Partner",
                        "UTC",
                        LocalDate.parse("2026-03-18"),
                        "track-2",
                        "Partner Song",
                        2
                )));

        SongOfDayService service = new SongOfDayService(songOfDayRepository, spotifyAccountRepository, clock);

        SongOfDayResponse response = service.getCurrentSongOfDay("partner");

        assertThat(response.spotifyUserId()).isEqualTo("partner");
        assertThat(response.trackName()).isEqualTo("Partner Song");
    }

    @Test
    void getCurrentSongOfDayReturnsUnavailableWhenNoAccountIsLinked() {
        SongOfDayRepository songOfDayRepository = mock(SongOfDayRepository.class);
        SpotifyAccountRepository spotifyAccountRepository = mock(SpotifyAccountRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T03:00:00Z"), ZoneOffset.UTC);

        when(spotifyAccountRepository.findMostRecentAccountIdentity()).thenReturn(Optional.empty());

        SongOfDayService service = new SongOfDayService(songOfDayRepository, spotifyAccountRepository, clock);

        SongOfDayResponse response = service.getCurrentSongOfDay(null);

        assertThat(response).isEqualTo(SongOfDayResponse.unavailable());
    }
}
