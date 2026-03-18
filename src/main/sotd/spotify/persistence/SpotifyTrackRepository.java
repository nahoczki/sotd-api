package sotd.spotify.persistence;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sotd.spotify.client.SpotifyRecentlyPlayedResponse;

/**
 * Persists normalized Spotify track and artist metadata.
 */
@Repository
public class SpotifyTrackRepository {

    private final JdbcClient jdbcClient;

    public SpotifyTrackRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void upsertTrack(SpotifyRecentlyPlayedResponse.Track track) {
        jdbcClient.sql("""
                insert into spotify_track (
                    spotify_track_id,
                    name,
                    album_id,
                    album_name,
                    duration_ms,
                    explicit,
                    external_url,
                    image_url,
                    updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                on conflict (spotify_track_id) do update
                set name = excluded.name,
                    album_id = excluded.album_id,
                    album_name = excluded.album_name,
                    duration_ms = excluded.duration_ms,
                    explicit = excluded.explicit,
                    external_url = excluded.external_url,
                    image_url = excluded.image_url,
                    updated_at = current_timestamp
                """)
                .params(
                        track.id(),
                        track.name(),
                        track.album() != null ? track.album().id() : null,
                        track.album() != null ? track.album().name() : null,
                        track.durationMs(),
                        track.explicit(),
                        track.externalUrls() != null ? track.externalUrls().spotify() : null,
                        firstImageUrl(track.album())
                )
                .update();
    }

    public void upsertArtists(List<SpotifyRecentlyPlayedResponse.Artist> artists) {
        for (SpotifyRecentlyPlayedResponse.Artist artist : artists) {
            jdbcClient.sql("""
                    insert into spotify_artist (
                        spotify_artist_id,
                        name,
                        external_url,
                        updated_at
                    ) values (?, ?, ?, current_timestamp)
                    on conflict (spotify_artist_id) do update
                    set name = excluded.name,
                        external_url = excluded.external_url,
                        updated_at = current_timestamp
                    """)
                    .params(
                            artist.id(),
                            artist.name(),
                            artist.externalUrls() != null ? artist.externalUrls().spotify() : null
                    )
                    .update();
        }
    }

    public void replaceTrackArtists(String trackId, List<SpotifyRecentlyPlayedResponse.Artist> artists) {
        jdbcClient.sql("delete from spotify_track_artist where spotify_track_id = ?")
                .param(trackId)
                .update();

        for (int index = 0; index < artists.size(); index++) {
            SpotifyRecentlyPlayedResponse.Artist artist = artists.get(index);
            jdbcClient.sql("""
                    insert into spotify_track_artist (
                        spotify_track_id,
                        spotify_artist_id,
                        artist_order
                    ) values (?, ?, ?)
                    on conflict (spotify_track_id, spotify_artist_id) do update
                    set artist_order = excluded.artist_order
                    """)
                    .params(trackId, artist.id(), index)
                    .update();
        }
    }

    private static String firstImageUrl(SpotifyRecentlyPlayedResponse.Album album) {
        if (album == null || album.images() == null || album.images().isEmpty()) {
            return null;
        }
        return album.images().getFirst().url();
    }
}
