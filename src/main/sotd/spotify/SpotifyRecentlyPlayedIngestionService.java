package sotd.spotify;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import sotd.spotify.client.SpotifyRecentlyPlayedResponse;
import sotd.spotify.persistence.PlaybackEventRepository;
import sotd.spotify.persistence.SpotifyPollingAccount;
import sotd.spotify.persistence.SpotifyTrackRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Normalizes recently-played Spotify payloads into relational tables.
 */
@Service
public class SpotifyRecentlyPlayedIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyRecentlyPlayedIngestionService.class);

    private final SpotifyTrackRepository spotifyTrackRepository;
    private final PlaybackEventRepository playbackEventRepository;
    private final ObjectMapper objectMapper;

    public SpotifyRecentlyPlayedIngestionService(
            SpotifyTrackRepository spotifyTrackRepository,
            PlaybackEventRepository playbackEventRepository,
            ObjectMapper objectMapper
    ) {
        this.spotifyTrackRepository = spotifyTrackRepository;
        this.playbackEventRepository = playbackEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecentlyPlayedIngestionResult ingest(
            SpotifyPollingAccount account,
            SpotifyRecentlyPlayedResponse response
    ) {
        Set<LocalDate> affectedDays = new HashSet<>();
        long newestCursorMs = account.lastRecentlyPlayedCursorMs() != null ? account.lastRecentlyPlayedCursorMs() : 0L;
        int insertedEvents = 0;

        List<SpotifyRecentlyPlayedResponse.PlayHistoryItem> items = response.items() != null
                ? response.items()
                : List.of();

        for (SpotifyRecentlyPlayedResponse.PlayHistoryItem item : items) {
            if (item == null || item.track() == null || item.playedAt() == null) {
                continue;
            }
            if (item.track().local() || !StringUtils.hasText(item.track().id())) {
                log.debug("Skipping non-catalog Spotify play for account {}.", account.spotifyUserId());
                continue;
            }

            spotifyTrackRepository.upsertTrack(item.track());
            spotifyTrackRepository.upsertArtists(item.track().artists() != null ? item.track().artists() : List.of());
            spotifyTrackRepository.replaceTrackArtists(item.track().id(), item.track().artists() != null ? item.track().artists() : List.of());

            LocalDate playedDateLocal = toLocalDate(item.playedAt(), account.timezone());
            boolean inserted = playbackEventRepository.insertIfAbsent(
                    account.id(),
                    item.track().id(),
                    item.playedAt(),
                    playedDateLocal,
                    item.context() != null ? item.context().type() : null,
                    item.context() != null ? item.context().uri() : null,
                    toJson(item)
            );
            if (inserted) {
                insertedEvents++;
                affectedDays.add(playedDateLocal);
            }

            newestCursorMs = Math.max(newestCursorMs, item.playedAt().toEpochMilli());
        }

        Long responseCursorMs = parseCursor(response);
        if (responseCursorMs != null) {
            newestCursorMs = Math.max(newestCursorMs, responseCursorMs);
        }

        return new RecentlyPlayedIngestionResult(insertedEvents, newestCursorMs > 0 ? newestCursorMs : null, affectedDays);
    }

    private LocalDate toLocalDate(Instant playedAt, String timezone) {
        String zoneId = StringUtils.hasText(timezone) ? timezone : "UTC";
        return playedAt.atZone(ZoneId.of(zoneId)).toLocalDate();
    }

    private String toJson(SpotifyRecentlyPlayedResponse.PlayHistoryItem item) {
        try {
            return objectMapper.writeValueAsString(item);
        }
        catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize Spotify recently-played payload.", ex);
        }
    }

    private static Long parseCursor(SpotifyRecentlyPlayedResponse response) {
        if (response.cursors() == null || !StringUtils.hasText(response.cursors().after())) {
            return null;
        }
        try {
            return Long.parseLong(response.cursors().after());
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record RecentlyPlayedIngestionResult(
            int insertedEvents,
            Long newestCursorMs,
            Set<LocalDate> affectedDays
    ) {
    }
}
