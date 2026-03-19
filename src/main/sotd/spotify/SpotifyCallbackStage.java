package sotd.spotify;

/**
 * Stages of the Spotify OAuth callback flow that can fail independently.
 */
public enum SpotifyCallbackStage {
    CONFIGURATION,
    STATE_VALIDATION,
    AUTHORIZATION_DENIED,
    MISSING_CODE,
    TOKEN_EXCHANGE,
    PROFILE_LOOKUP,
    PERSISTENCE
}
