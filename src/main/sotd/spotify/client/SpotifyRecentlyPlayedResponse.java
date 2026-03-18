package sotd.spotify.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record SpotifyRecentlyPlayedResponse(
        List<PlayHistoryItem> items,
        Cursors cursors
) {

    public record Cursors(
            String after,
            String before
    ) {
    }

    public record PlayHistoryItem(
            Track track,
            @JsonProperty("played_at") Instant playedAt,
            Context context
    ) {
    }

    public record Context(
            String type,
            String uri
    ) {
    }

    public record Track(
            String id,
            String name,
            @JsonProperty("duration_ms") int durationMs,
            boolean explicit,
            @JsonProperty("external_urls") ExternalUrls externalUrls,
            Album album,
            List<Artist> artists,
            @JsonProperty("is_local") boolean local
    ) {
    }

    public record Album(
            String id,
            String name,
            List<Image> images
    ) {
    }

    public record Artist(
            String id,
            String name,
            @JsonProperty("external_urls") ExternalUrls externalUrls
    ) {
    }

    public record Image(
            String url,
            Integer height,
            Integer width
    ) {
    }

    public record ExternalUrls(
            String spotify
    ) {
    }
}
