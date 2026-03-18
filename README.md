# sotd-api

Private Spring Boot API for tracking Spotify listening history and surfacing user-scoped "song of the day" results backed by PostgreSQL.

## Current Status

Implemented now:

- Spring Boot 4 baseline on Java 21
- PostgreSQL local Docker setup
- Flyway-managed schema and schema evolution
- shallow source layout under `src/main/sotd`
- Spotify Authorization Code flow
- encrypted refresh-token storage
- automatic access-token refresh for polling
- recently-played polling and ingestion
- daily song rollups and winner computation
- user-scoped read endpoints under `/api/users/{appUserId}/...`

Not implemented yet:

- frontend callback redirect flow
- weekly, monthly, and yearly winner reads
- deployment-grade shared OAuth state storage

## Stack

- Java 21
- Spring Boot 4
- Spring MVC
- Spring JDBC / `JdbcClient` direction
- Flyway
- PostgreSQL 18
- Docker Compose

## Project Goal

The long-term goal is to:

1. connect a Spotify account once through backend-managed OAuth
2. bind that Spotify account to a stable upstream `app_user_id`
3. ingest listening history into PostgreSQL
4. compute daily, weekly, monthly, and yearly winners from stored play events
5. expose fast frontend-facing endpoints such as `song-of-the-day`

## Project Layout

This project intentionally uses a flatter Java layout than the default IntelliJ/Spring generator layout.

- `src/main/sotd` - application code
- `src/main/resources` - app config and Flyway migrations
- `src/test/sotd` - tests
- `ai-reports` - architecture, stack, and setup reports

## Local Development

### Requirements

- Java 21
- Docker Desktop

### Start PostgreSQL

From the repo root:

```powershell
docker compose up -d postgres
```

Local DB defaults:

- host: `localhost`
- port: `5432`
- database: `sotd`
- username: `sotd`
- password: `sotd`

### Run the app

```powershell
.\gradlew.bat bootRun
```

On startup, Flyway will apply the initial schema migration automatically.

### Run tests

```powershell
.\gradlew.bat fullSuite
```

### Current API shape

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/users/{appUserId}/song-of-the-day
```

Key routes:

- `GET /api/users/{appUserId}/spotify/connect`
- `GET /api/spotify/callback`
- `GET /api/users/{appUserId}/spotify/connection`
- `GET /api/users/{appUserId}/song-of-the-day`

The backend does not create users locally. It expects `{appUserId}` to come from your upstream account system.

User-scoped routes are protected by a signed upstream auth token. The upstream backend is expected to:

- mint a short-lived token for a specific `app_user_id`
- send it in the `X-SOTD-UPSTREAM-AUTH` header for server-to-server reads
- append it as the `upstreamAuth` query parameter for browser redirects to `/spotify/connect`

## Database

The initial schema is defined in:

- `src/main/resources/db/migration/V1__create_sotd_core_schema.sql`

Current core tables:

- `spotify_account`
- `spotify_track`
- `spotify_artist`
- `spotify_track_artist`
- `playback_event`
- `song_period_rollup`
- `song_period_winner`

## Spotify Setup Notes

Local callback URI:

- `http://127.0.0.1:8080/api/spotify/callback`

Local connect flow:

- generate a short-lived upstream auth token for the target UUID
- open `http://127.0.0.1:8080/api/users/{appUserId}/spotify/connect?upstreamAuth={token}` in a browser
- complete Spotify auth
- inspect the linked account with `X-SOTD-UPSTREAM-AUTH: {token}` at `http://127.0.0.1:8080/api/users/{appUserId}/spotify/connection`
- read the winner with `X-SOTD-UPSTREAM-AUTH: {token}` at `http://127.0.0.1:8080/api/users/{appUserId}/song-of-the-day`

If you linked accounts before the `app_user_id` migration, re-run the connect flow through the user-scoped URL so the existing `spotify_account` row is attached to the correct UUID.

## Next Recommended Work

1. Finalize the token-minting contract on the upstream backend that will call this service.
2. Expose weekly, monthly, and yearly winner endpoints.
3. Replace in-memory OAuth state with a shared store for multi-instance deployment.
4. Add operational dashboards around polling success, lag, and reauthorization status.
5. Decide whether this service should eventually validate upstream users directly or remain trust-boundary only.
