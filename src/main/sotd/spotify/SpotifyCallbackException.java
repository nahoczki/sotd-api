package sotd.spotify;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Structured callback failure used to keep `/api/spotify/callback` responses predictable.
 */
public class SpotifyCallbackException extends RuntimeException {

    private final HttpStatus status;
    private final SpotifyCallbackStage stage;
    private final String errorCode;
    private final String userMessage;
    private final UUID appUserId;

    private SpotifyCallbackException(
            HttpStatus status,
            SpotifyCallbackStage stage,
            String errorCode,
            String userMessage,
            UUID appUserId,
            Throwable cause
    ) {
        super(userMessage, cause);
        this.status = status;
        this.stage = stage;
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.appUserId = appUserId;
    }

    public static SpotifyCallbackException configuration(String userMessage, Throwable cause) {
        return new SpotifyCallbackException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                SpotifyCallbackStage.CONFIGURATION,
                "spotify_callback_configuration_error",
                userMessage,
                null,
                cause
        );
    }

    public static SpotifyCallbackException invalidState(String userMessage) {
        return new SpotifyCallbackException(
                HttpStatus.BAD_REQUEST,
                SpotifyCallbackStage.STATE_VALIDATION,
                "spotify_callback_invalid_state",
                userMessage,
                null,
                null
        );
    }

    public static SpotifyCallbackException authorizationDenied(UUID appUserId, String userMessage) {
        return new SpotifyCallbackException(
                HttpStatus.BAD_REQUEST,
                SpotifyCallbackStage.AUTHORIZATION_DENIED,
                "spotify_callback_authorization_denied",
                userMessage,
                appUserId,
                null
        );
    }

    public static SpotifyCallbackException missingCode(UUID appUserId, String userMessage) {
        return new SpotifyCallbackException(
                HttpStatus.BAD_REQUEST,
                SpotifyCallbackStage.MISSING_CODE,
                "spotify_callback_missing_code",
                userMessage,
                appUserId,
                null
        );
    }

    public static SpotifyCallbackException tokenExchange(UUID appUserId, String userMessage, Throwable cause) {
        return new SpotifyCallbackException(
                HttpStatus.BAD_GATEWAY,
                SpotifyCallbackStage.TOKEN_EXCHANGE,
                "spotify_callback_token_exchange_failed",
                userMessage,
                appUserId,
                cause
        );
    }

    public static SpotifyCallbackException profileLookup(UUID appUserId, String userMessage, Throwable cause) {
        return new SpotifyCallbackException(
                HttpStatus.BAD_GATEWAY,
                SpotifyCallbackStage.PROFILE_LOOKUP,
                "spotify_callback_profile_lookup_failed",
                userMessage,
                appUserId,
                cause
        );
    }

    public static SpotifyCallbackException persistence(UUID appUserId, String userMessage, Throwable cause) {
        return new SpotifyCallbackException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                SpotifyCallbackStage.PERSISTENCE,
                "spotify_callback_persistence_failed",
                userMessage,
                appUserId,
                cause
        );
    }

    public HttpStatus getStatus() {
        return status;
    }

    public SpotifyCallbackStage getStage() {
        return stage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public UUID getAppUserId() {
        return appUserId;
    }
}
