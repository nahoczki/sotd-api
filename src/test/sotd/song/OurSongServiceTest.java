package sotd.song;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import sotd.spotify.persistence.SpotifyAccountRepository;
import sotd.spotify.persistence.SpotifyAccountRepository.LinkedSpotifyAccountIdentity;

class OurSongServiceTest {

    @Test
    void getCurrentSharedSongReturnsCurrentMatchForRequestedUsers() {
        OurSongRepository ourSongRepository = mock(OurSongRepository.class);
        SpotifyAccountRepository spotifyAccountRepository = mock(SpotifyAccountRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T03:00:00Z"), ZoneOffset.UTC);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(spotifyAccountRepository.findAccountIdentityByAppUserId(appUserId))
                .thenReturn(Optional.of(new LinkedSpotifyAccountIdentity(appUserId, "lukerykta", "America/New_York")));
        when(spotifyAccountRepository.findAccountIdentityByAppUserId(otherUserId))
                .thenReturn(Optional.of(new LinkedSpotifyAccountIdentity(otherUserId, "partner", "America/New_York")));
        when(ourSongRepository.findBestSharedSong(
                appUserId,
                otherUserId,
                OurSongPeriodType.DAY,
                LocalDate.parse("2026-03-17"),
                LocalDate.parse("2026-03-18")
        )).thenReturn(Optional.of(new OurSongMatchView(
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
        )));

        OurSongService service = new OurSongService(ourSongRepository, spotifyAccountRepository, clock);

        OurSongResponse response = service.getCurrentSharedSong(appUserId, otherUserId, OurSongPeriodType.DAY);

        assertThat(response.status()).isEqualTo("ready");
        assertThat(response.periodStartLocal()).isEqualTo(LocalDate.parse("2026-03-17"));
        assertThat(response.trackName()).isEqualTo("Track Name");
        assertThat(response.combinedPlayCount()).isEqualTo(8);
    }

    @Test
    void getCurrentSharedSongReturnsUnlinkedWhenRequestingUserHasNoSpotifyAccount() {
        OurSongRepository ourSongRepository = mock(OurSongRepository.class);
        SpotifyAccountRepository spotifyAccountRepository = mock(SpotifyAccountRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T03:00:00Z"), ZoneOffset.UTC);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(spotifyAccountRepository.findAccountIdentityByAppUserId(appUserId)).thenReturn(Optional.empty());

        OurSongService service = new OurSongService(ourSongRepository, spotifyAccountRepository, clock);

        OurSongResponse response = service.getCurrentSharedSong(appUserId, otherUserId, OurSongPeriodType.DAY);

        assertThat(response.status()).isEqualTo("unlinked");
        assertThat(response.message()).isEqualTo("No Spotify account is linked for the requesting user.");
        assertThat(response.periodStartLocal()).isNull();
    }

    @Test
    void getCurrentSharedSongReturnsUnlinkedWhenComparisonUserHasNoSpotifyAccount() {
        OurSongRepository ourSongRepository = mock(OurSongRepository.class);
        SpotifyAccountRepository spotifyAccountRepository = mock(SpotifyAccountRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T03:00:00Z"), ZoneOffset.UTC);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(spotifyAccountRepository.findAccountIdentityByAppUserId(appUserId))
                .thenReturn(Optional.of(new LinkedSpotifyAccountIdentity(appUserId, "lukerykta", "America/New_York")));
        when(spotifyAccountRepository.findAccountIdentityByAppUserId(otherUserId)).thenReturn(Optional.empty());

        OurSongService service = new OurSongService(ourSongRepository, spotifyAccountRepository, clock);

        OurSongResponse response = service.getCurrentSharedSong(appUserId, otherUserId, OurSongPeriodType.DAY);

        assertThat(response.status()).isEqualTo("unlinked");
        assertThat(response.message()).isEqualTo("No Spotify account is linked for the comparison user.");
        assertThat(response.periodStartLocal()).isEqualTo(LocalDate.parse("2026-03-17"));
    }

    @Test
    void getCurrentSharedSongReturnsNoCommonSongWhenNoOverlapExists() {
        OurSongRepository ourSongRepository = mock(OurSongRepository.class);
        SpotifyAccountRepository spotifyAccountRepository = mock(SpotifyAccountRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-19T03:00:00Z"), ZoneOffset.UTC);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(spotifyAccountRepository.findAccountIdentityByAppUserId(appUserId))
                .thenReturn(Optional.of(new LinkedSpotifyAccountIdentity(appUserId, "lukerykta", "America/New_York")));
        when(spotifyAccountRepository.findAccountIdentityByAppUserId(otherUserId))
                .thenReturn(Optional.of(new LinkedSpotifyAccountIdentity(otherUserId, "partner", "America/New_York")));
        when(ourSongRepository.findBestSharedSong(
                appUserId,
                otherUserId,
                OurSongPeriodType.WEEK,
                LocalDate.parse("2026-03-16"),
                LocalDate.parse("2026-03-23")
        )).thenReturn(Optional.empty());

        OurSongService service = new OurSongService(ourSongRepository, spotifyAccountRepository, clock);

        OurSongResponse response = service.getCurrentSharedSong(appUserId, otherUserId, OurSongPeriodType.WEEK);

        assertThat(response.status()).isEqualTo("no-common-song");
        assertThat(response.periodType()).isEqualTo(OurSongPeriodType.WEEK);
        assertThat(response.periodStartLocal()).isEqualTo(LocalDate.parse("2026-03-16"));
        verify(ourSongRepository).findBestSharedSong(
                appUserId,
                otherUserId,
                OurSongPeriodType.WEEK,
                LocalDate.parse("2026-03-16"),
                LocalDate.parse("2026-03-23")
        );
    }

    @Test
    void getCurrentSharedSongRejectsSelfComparisons() {
        OurSongRepository ourSongRepository = mock(OurSongRepository.class);
        SpotifyAccountRepository spotifyAccountRepository = mock(SpotifyAccountRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T03:00:00Z"), ZoneOffset.UTC);
        UUID appUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        OurSongService service = new OurSongService(ourSongRepository, spotifyAccountRepository, clock);

        assertThatThrownBy(() -> service.getCurrentSharedSong(appUserId, appUserId, OurSongPeriodType.DAY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("distinct users");
    }
}
