package sotd.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sotd.config.OpenApiConfig;
import sotd.song.SongOfDayResponse;
import sotd.song.SongOfDayService;

@RestController
@RequestMapping("/api/users/{appUserId}")
@Tag(name = "song-of-the-day")
public class SongOfDayController {

    private final SongOfDayService songOfDayService;

    public SongOfDayController(SongOfDayService songOfDayService) {
        this.songOfDayService = songOfDayService;
    }

    @GetMapping("/song-of-the-day")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Get song of the day",
            description = "Returns the current daily winner for the requested application user.",
            security = @SecurityRequirement(name = OpenApiConfig.UPSTREAM_HEADER_AUTH_SCHEME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Song-of-the-day state returned."),
            @ApiResponse(responseCode = "401", description = "Missing or invalid upstream auth token.", content = @Content),
            @ApiResponse(responseCode = "403", description = "Upstream token does not match the requested UUID.", content = @Content)
    })
    public SongOfDayResponse getSongOfTheDay(
            @Parameter(description = "Stable upstream application user UUID.", required = true)
            @PathVariable UUID appUserId
    ) {
        return songOfDayService.getCurrentSongOfDay(appUserId);
    }
}
