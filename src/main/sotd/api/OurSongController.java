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
import sotd.song.OurSongPeriodType;
import sotd.song.OurSongResponse;
import sotd.song.OurSongService;

@RestController
@RequestMapping("/api/users/{appUserId}")
@Tag(name = "our-song")
public class OurSongController {

    private final OurSongService ourSongService;

    public OurSongController(OurSongService ourSongService) {
        this.ourSongService = ourSongService;
    }

    @GetMapping("/our-song/{otherUserId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Get the shared song for two users",
            description = "Returns the highest-ranked song both users played during the same comparison window.",
            security = @SecurityRequirement(name = OpenApiConfig.UPSTREAM_HEADER_AUTH_SCHEME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shared-song state returned."),
            @ApiResponse(responseCode = "400", description = "Invalid comparison request.", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or invalid upstream auth token.", content = @Content),
            @ApiResponse(responseCode = "403", description = "Upstream token does not match the requested UUID.", content = @Content)
    })
    public OurSongResponse getOurSong(
            @Parameter(description = "Stable upstream application user UUID for the requesting profile.", required = true)
            @PathVariable UUID appUserId,
            @Parameter(description = "Stable upstream application user UUID for the comparison profile.", required = true)
            @PathVariable UUID otherUserId,
            @Parameter(
                    description = "Comparison period.",
                    in = ParameterIn.QUERY
            )
            @RequestParam(defaultValue = "DAY") OurSongPeriodType period
    ) {
        return ourSongService.getCurrentSharedSong(appUserId, otherUserId, period);
    }
}
