package sotd.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import sotd.spotify.client.SpotifyRecentlyPlayedResponse;
import sotd.spotify.persistence.PlaybackEventRepository;
import sotd.spotify.persistence.SpotifyPollingAccount;
import sotd.spotify.persistence.SpotifyTrackRepository;
import tools.jackson.databind.ObjectMapper;

class SpotifyRecentlyPlayedIngestionServiceTest {

    @Test
    void ingestPersistsTrackMetadataAndPlaybackEvents() throws Exception {
        SpotifyTrackRepository trackRepository = mock(SpotifyTrackRepository.class);
        PlaybackEventRepository playbackEventRepository = mock(PlaybackEventRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"played_at\":\"2026-03-18T02:15:00Z\"}");
        when(playbackEventRepository.insertIfAbsent(
                eq(9L),
                eq("track-1"),
                eq(Instant.parse("2026-03-18T02:15:00Z")),
                eq(LocalDate.parse("2026-03-17")),
                eq("playlist"),
                eq("spotify:playlist:abc"),
                any(String.class)
        )).thenReturn(true);

        SpotifyRecentlyPlayedIngestionService service = new SpotifyRecentlyPlayedIngestionService(
                trackRepository,
                playbackEventRepository,
                objectMapper
        );

        SpotifyRecentlyPlayedResponse response = new SpotifyRecentlyPlayedResponse(
                List.of(playHistoryItem(false, "track-1")),
                new SpotifyRecentlyPlayedResponse.Cursors("1773790500000", null)
        );

        SpotifyRecentlyPlayedIngestionService.RecentlyPlayedIngestionResult result = service.ingest(
                new SpotifyPollingAccount(9L, "spotify-user", new byte[] {1}, null, null, "America/New_York"),
                response
        );

        assertThat(result.insertedEvents()).isEqualTo(1);
        assertThat(result.newestCursorMs()).isEqualTo(1773800100000L);
        assertThat(result.affectedDays()).containsExactly(LocalDate.parse("2026-03-17"));

        verify(trackRepository).upsertTrack(any(SpotifyRecentlyPlayedResponse.Track.class));
        verify(trackRepository).upsertArtists(anyList());
        verify(trackRepository).replaceTrackArtists(eq("track-1"), anyList());
    }

    @Test
    void ingestSkipsLocalTracksWithoutCatalogIds() {
        SpotifyTrackRepository trackRepository = mock(SpotifyTrackRepository.class);
        PlaybackEventRepository playbackEventRepository = mock(PlaybackEventRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        SpotifyRecentlyPlayedIngestionService service = new SpotifyRecentlyPlayedIngestionService(
                trackRepository,
                playbackEventRepository,
                objectMapper
        );

        SpotifyRecentlyPlayedResponse response = new SpotifyRecentlyPlayedResponse(
                List.of(playHistoryItem(true, null)),
                new SpotifyRecentlyPlayedResponse.Cursors(null, null)
        );

        SpotifyRecentlyPlayedIngestionService.RecentlyPlayedIngestionResult result = service.ingest(
                new SpotifyPollingAccount(9L, "spotify-user", new byte[] {1}, null, null, "UTC"),
                response
        );

        assertThat(result.insertedEvents()).isZero();
        assertThat(result.affectedDays()).isEmpty();
        verifyNoInteractions(trackRepository, playbackEventRepository);
    }

    private static SpotifyRecentlyPlayedResponse.PlayHistoryItem playHistoryItem(boolean local, String trackId) {
        return new SpotifyRecentlyPlayedResponse.PlayHistoryItem(
                new SpotifyRecentlyPlayedResponse.Track(
                        trackId,
                        "Track Name",
                        200000,
                        false,
                        new SpotifyRecentlyPlayedResponse.ExternalUrls("https://open.spotify.test/track"),
                        new SpotifyRecentlyPlayedResponse.Album(
                                "album-1",
                                "Album Name",
                                List.of(new SpotifyRecentlyPlayedResponse.Image("https://image.test", 640, 640))
                        ),
                        List.of(new SpotifyRecentlyPlayedResponse.Artist(
                                "artist-1",
                                "Artist Name",
                                new SpotifyRecentlyPlayedResponse.ExternalUrls("https://open.spotify.test/artist")
                        )),
                        local
                ),
                Instant.parse("2026-03-18T02:15:00Z"),
                new SpotifyRecentlyPlayedResponse.Context("playlist", "spotify:playlist:abc")
        );
    }
}
