package sotd.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sotd.config.OpenApiConfig;
import sotd.song.SongPeriodType;
import sotd.song.TopSongResponse;
import sotd.song.TopSongService;

@RestController
@RequestMapping("/api/users/{appUserId}")
@Tag(name = "top-song")
public class TopSongController {

    private final TopSongService topSongService;

    public TopSongController(TopSongService topSongService) {
        this.topSongService = topSongService;
    }

    @GetMapping("/top-song")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Get the top song for a user and period",
            description = "Returns the highest-ranked song for the requested application user and comparison period.",
            security = @SecurityRequirement(name = OpenApiConfig.UPSTREAM_HEADER_AUTH_SCHEME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Top-song state returned."),
            @ApiResponse(responseCode = "401", description = "Missing or invalid upstream auth token.", content = @Content),
            @ApiResponse(responseCode = "403", description = "Upstream token does not match the requested UUID.", content = @Content)
    })
    public TopSongResponse getTopSong(
            @Parameter(description = "Stable upstream application user UUID.", required = true)
            @PathVariable UUID appUserId,
            @Parameter(
                    description = "Requested ranking period.",
                    in = ParameterIn.QUERY
            )
            @RequestParam(defaultValue = "DAY") SongPeriodType period
    ) {
        return topSongService.getTopSong(appUserId, period);
    }
}
