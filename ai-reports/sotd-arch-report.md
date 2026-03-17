# Spotify SOTD Architecture Report

Date: 2026-03-17

## Scope

Assess the suitability of building this API around Spotify Web API data so the frontend can fetch a user's "song of the day" (SOTD), with room to expand into week, month, and year views later.

This report is based on:

- The current repository state, which is a fresh Spring Boot/MySQL skeleton with Flyway but no web, security, scheduling, or persistence implementation yet.
- Spotify's current public Web API docs and Developer Policy as of 2026-03-17.

## Repository Baseline

Current project state:

- Spring Boot `4.0.3`
- Java toolchain `25`
- Flyway + MySQL already present
- `compose.yaml` contains a basic MySQL container
- No controllers, auth flow, schedulers, repositories, or DB migrations exist yet

Immediate backend gaps if this project is going to serve a frontend:

- `spring-boot-starter-web`
- either `spring-boot-starter-jdbc` or `spring-boot-starter-data-jpa`
- OAuth/client code for Spotify
- scheduling support for polling workers
- validation/error handling
- a secure secret-management approach for refresh tokens

## Executive Summary

The design is technically feasible for a small, controlled app, especially if the target is one Spotify user or a very small allowlisted set of users.

The main conclusion is:

- `Get User's Top Items` is not suitable as the primary source for "song of the day."
- A polling + persistence design is the only viable technical approach with Spotify's public Web API because Spotify does not provide a historical daily play-count endpoint or webhook for this use case.
- The best source of truth for historical aggregation is `Get Recently Played Tracks`, not `Get Currently Playing Track` and not `Get Playback State`.
- The biggest blockers are not only technical. Spotify's current quota model and Developer Policy create real product risk if this is meant to scale beyond a personal or tightly limited app.

## Suitability of `Get User's Top Items`

Verdict: unsuitable for daily SOTD, acceptable only as a secondary or fallback signal.

Why:

- Spotify defines this endpoint as returning top artists or tracks based on "calculated affinity," not raw counted plays.
- Its time windows are fixed to `short_term` (~4 weeks), `medium_term` (~6 months), and `long_term` (~1 year).
- It does not support arbitrary day, week, month, or year boundaries.
- It does not expose raw per-track play counts that you can reuse for your own ranking logic.

Implication:

- If your frontend asks `getSongOfTheDay`, `Get User's Top Items` cannot answer that question correctly.
- It may be useful later for a separate endpoint like "Spotify top tracks snapshot," but not as the core SOTD engine.

## Recommended Technical Direction

Use a three-layer design:

1. Spotify auth and token management
2. Background ingestion of playback history into your DB
3. Fast read endpoints backed by rollups/materialized results

### Core principle

Treat Spotify as an upstream event source, not as the live query engine for SOTD.

The API endpoint that your frontend calls should read your database, not compute from Spotify in real time.

## Recommended Backend Components

### 1. OAuth and account-linking

Needed endpoints:

- `GET /api/spotify/connect`
- `GET /api/spotify/callback`
- `DELETE /api/spotify/connection`

What this layer must do:

- Start Spotify Authorization Code flow
- Request only the scopes you need
- verify the OAuth `state` parameter
- exchange the authorization code for access + refresh tokens
- store the Spotify user ID and granted scopes
- support reconnect/disconnect

Recommended scopes for this use case:

- `user-read-recently-played`
- `user-read-playback-state`
- `user-read-currently-playing`
- `user-read-private` if you want `/me` to identify the Spotify account and inspect subscription details

### 2. Token service

Responsibilities:

- keep refresh token encrypted at rest
- keep access tokens short-lived and preferably in memory or cached with expiry metadata
- refresh access tokens automatically before expiry or on `401`
- preserve the old refresh token when Spotify refreshes the access token but does not return a new refresh token

Recommended storage approach:

- store refresh tokens encrypted with AES-GCM using a master key from a secret manager or deployment secret
- never log access tokens or refresh tokens
- keep audit fields: `created_at`, `updated_at`, `last_refresh_at`, `token_expires_at`, `scope`

### 3. Spotify ingestion worker

Use `Get Recently Played Tracks` as the primary ingestion endpoint.

Reason:

- it returns historical play objects with `played_at`
- it supports cursoring with `after` and `before`
- it is better aligned with completed playback history than "currently playing" snapshots

Use `Get Playback State` and/or `Get Currently Playing Track` only for:

- a near-real-time "now playing" endpoint
- adaptive polling decisions
- detecting active playback sessions

### 4. Rollup and query layer

Do not compute SOTD directly from raw Spotify calls during frontend requests.

Instead:

- ingest raw play events
- aggregate them into daily/weekly/monthly/yearly rollups
- optionally materialize the current winner per period

This keeps the frontend path fast and stable.

## Recommended Data Model

Suggested minimum tables:

### `spotify_account`

- `id`
- `app_user_id`
- `spotify_user_id`
- `display_name`
- `scope`
- `refresh_token_encrypted`
- `access_token_expires_at`
- `last_successful_poll_at`
- `last_recently_played_cursor_ms`
- `timezone`
- `status`

Notes:

- `spotify_user_id` should be unique
- `timezone` matters because "song of the day" depends on local day boundaries

### `spotify_track`

- `spotify_track_id`
- `name`
- `album_id`
- `album_name`
- `duration_ms`
- `explicit`
- `external_url`
- `image_url`
- metadata timestamps

### `spotify_artist`

- `spotify_artist_id`
- `name`
- `external_url`

### `spotify_track_artist`

- `spotify_track_id`
- `spotify_artist_id`
- `artist_order`

### `playback_event`

- `id`
- `spotify_account_id`
- `spotify_track_id`
- `played_at_utc`
- `played_date_local`
- `source_context_type`
- `source_context_uri`
- `raw_payload_json` optional
- unique key to prevent duplicate ingestion

Inference:

- The Web API does not expose an official raw play-count metric for your use case, so the safest implementation is to treat each returned recently-played item as one playback event.

### `song_period_rollup`

- `spotify_account_id`
- `period_type` (`DAY`, `WEEK`, `MONTH`, `YEAR`)
- `period_start_local`
- `spotify_track_id`
- `play_count`
- `total_duration_ms`
- `last_played_at_utc`

### `song_period_winner`

- `spotify_account_id`
- `period_type`
- `period_start_local`
- `spotify_track_id`
- `play_count`
- `tie_break_rule`
- `computed_at`

## Polling Strategy

### Recommended strategy

Primary poller:

- poll `GET /me/player/recently-played`
- use `after=last_recently_played_cursor_ms`
- ingest new items in ascending time order
- update the cursor only after the transaction commits

Optional secondary poller:

- poll `GET /me/player` or `GET /me/player/currently-playing`
- use this for "now playing" UX and for adaptive scheduling

### Cadence

For a single user or a few users:

- recently-played every 1 to 5 minutes is realistic
- playback-state/currently-playing every 15 to 30 seconds only while active, otherwise back off

For larger user counts:

- 24/7 fixed high-frequency polling will become expensive and rate-limit-prone
- schedule adaptively based on recent activity
- use distributed locking if you run more than one application instance

### Why not rely on current playback endpoints alone

`Get Playback State` and `Get Currently Playing Track` are current snapshots. They are useful for live state, but by themselves they are a poor historical record. If your poll misses a track change, the DB misses it too.

## SOTD Calculation Logic

Store raw events and compute winners with explicit rules.

Recommended ranking order:

1. highest `play_count`
2. highest `total_duration_ms`
3. latest `last_played_at_utc`
4. lowest `spotify_track_id` as a deterministic final tie-breaker

Why this matters:

- "played 4 times" is easy to count
- ties are inevitable
- deterministic tie-breaking prevents unstable API responses

For fast reads, either:

- compute rollups incrementally after each ingestion batch, or
- run a frequent scheduled aggregation job and materialize the winner table

For future week/month/year expansion, the rollup table is the scalable path.

## Auth and Security Obstacles

### OAuth flow requirements

Spotify's Authorization Code flow is explicitly intended for long-running applications where the user grants permission once.

Important implementation details:

- `response_type` must be `code`
- `redirect_uri` must exactly match the URI used during authorization
- you must compare the returned `state` against the original one and reject mismatches

### Refresh-token handling

Spotify access tokens expire after 1 hour.

Operational requirements:

- refresh before or when needed
- serialize refresh attempts per account to avoid token stampedes
- handle revoked access by marking the account disconnected and requiring reauth

Important doc detail:

- Spotify may not return a new refresh token on each refresh response, so you must keep using the existing refresh token when none is returned

### Secure storage

Minimum bar:

- encrypted refresh token at rest
- secrets outside source control
- no token logging
- account disconnect flow that deletes user data tied to Spotify access when appropriate

## Quota and Scale Risks

### Development mode

Spotify's current docs say:

- new apps start in development mode
- development mode is appropriate for apps under construction or apps accessing a single Spotify account
- the app owner must have a Premium account for development mode apps to function
- only up to 5 authenticated Spotify users can use the app
- non-allowlisted users can authenticate but API calls will receive `403`

### Extended quota mode

This is the biggest scale blocker.

Spotify's current quota docs say that as of May 15, 2025:

- quota-extension applications are accepted only from organizations, not individuals
- applications must come from a company email
- one listed requirement is at least 250k MAUs

Implication:

- if this product is for yourself or a tiny controlled user set, the design is viable
- if this is intended to become a normal public multi-user product, Spotify's current quota program is a serious business blocker

### Rate limits

Spotify calculates rate limits in a rolling 30-second window.

Operational requirements:

- central request budgeting
- `429` handling
- obey `Retry-After`
- adaptive polling and jitter
- observability around request volume per account and per endpoint

## Policy and Compliance Risks

This section is important.

Spotify's Developer Policy effective May 15, 2025 includes language that creates risk for this exact product shape.

Relevant points from the policy:

- you must provide a privacy policy and only process the data needed for the app
- users must be able to disconnect their Spotify account
- when a user disconnects, you must delete and stop processing that user's personal data
- if you display Spotify metadata or cover art, it must be attributed to Spotify and linked back to the relevant Spotify item
- metadata/cover art may not be offered as a standalone service
- Spotify says you must not analyze Spotify content or the Spotify service to create "new or derived listenership metrics" or user metrics

Assessment:

- A daily "song of the day" ranking derived from captured playback history may be interpreted as a derived listenership metric.
- That makes this more than a technical architecture problem. It is a product-policy risk that should be resolved before heavy implementation work.

This is not legal advice, but it is a material compliance concern from Spotify's own published policy.

## Recommended API Surface

External endpoints:

- `GET /api/song-of-the-day`
- `GET /api/song-of-the-period?period=week|month|year&date=YYYY-MM-DD`
- `GET /api/spotify/now-playing`
- `GET /api/spotify/connect`
- `GET /api/spotify/callback`
- `DELETE /api/spotify/connection`

Internal/admin endpoints:

- `POST /internal/spotify/poll/{accountId}`
- `POST /internal/spotify/recompute-rollups/{accountId}`

## Recommended Build Order

1. Add web, persistence, and scheduling dependencies.
2. Create Flyway migrations for `spotify_account`, `spotify_track`, `spotify_artist`, `spotify_track_artist`, `playback_event`, `song_period_rollup`, and `song_period_winner`.
3. Implement Spotify Authorization Code flow and secure refresh-token storage.
4. Implement `/me` account-link verification and scope persistence.
5. Implement the recently-played ingestion worker with idempotent inserts.
6. Add a first-pass daily rollup job and `GET /api/song-of-the-day`.
7. Add adaptive polling and now-playing support.
8. Add week/month/year rollups.
9. Add disconnect/deletion flows and compliance checks.

## Final Recommendation

If the goal is a personal app or a very small private app, proceed with this shape:

- backend-managed Authorization Code flow
- encrypted refresh-token storage
- recently-played ingestion as the source of truth
- rollup tables for daily/weekly/monthly/yearly winners

If the goal is a broadly available public product, pause before implementation and resolve two issues first:

- whether Spotify's current quota model makes the product distributable at all
- whether Spotify's current Developer Policy permits this kind of derived SOTD metric

## Sources

- Spotify Web API: Get User's Top Items  
  https://developer.spotify.com/documentation/web-api/reference/get-users-top-artists-and-tracks
- Spotify Web API: Get Recently Played Tracks  
  https://developer.spotify.com/documentation/web-api/reference/get-recently-played
- Spotify Web API: Get Playback State  
  https://developer.spotify.com/documentation/web-api/reference/get-information-about-the-users-current-playback
- Spotify Web API: Get Currently Playing Track  
  https://developer.spotify.com/documentation/web-api/reference/get-the-users-currently-playing-track
- Spotify Web API: Get Current User's Profile  
  https://developer.spotify.com/documentation/web-api/reference/get-current-users-profile
- Spotify Web API Tutorial: Authorization Code Flow  
  https://developer.spotify.com/documentation/web-api/tutorials/code-flow
- Spotify Web API Tutorial: Refreshing Tokens  
  https://developer.spotify.com/documentation/web-api/tutorials/refreshing-tokens
- Spotify Web API Concepts: Rate Limits  
  https://developer.spotify.com/documentation/web-api/concepts/rate-limits
- Spotify Web API Concepts: Quota Modes  
  https://developer.spotify.com/documentation/web-api/concepts/quota-modes
- Spotify Developer Policy  
  https://developer.spotify.com/policy
