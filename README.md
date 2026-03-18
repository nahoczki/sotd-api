# sotd-api

Private Spring Boot API for tracking Spotify listening history and surfacing user-scoped top-song and pairwise our-song results backed by PostgreSQL.

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
- period-based top-song reads built from daily rollups
- pairwise shared-song reads built from daily rollups
- user-scoped read endpoints under `/api/users/{appUserId}/...`

Not implemented yet:

- frontend callback redirect flow
- weekly, monthly, and yearly winner reads
- multi-replica poller coordination

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
5. expose fast frontend-facing endpoints such as `top-song` and `our-song`

## Project Layout

This project intentionally uses a flatter Java layout than the default IntelliJ/Spring generator layout.

- `src/main/sotd` - application code
- `src/main/resources` - app config and Flyway migrations
- `src/test/sotd` - tests

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

For local endpoint testing without the upstream backend, use the `local` profile alias:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

`local` activates the `dev` profile under the hood and disables upstream JWT enforcement so you can test the user-scoped routes directly. In IntelliJ's Active profiles field, you can now just enter `local`.

On startup, Flyway will apply the initial schema migration automatically.

### Run tests

```powershell
.\gradlew.bat fullSuite
```

### Build container image

```powershell
docker build -t sotd-api:local .
```

Run it with environment variables supplied by your platform or an env file:

```powershell
docker run --rm -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=prod sotd-api:local
```

### Current API shape

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/api/users/{appUserId}/top-song?period=DAY"
```

Key routes:

- `GET /api/users/{appUserId}/spotify/connect`
- `GET /api/spotify/callback`
- `GET /api/users/{appUserId}/spotify/connection`
- `DELETE /api/users/{appUserId}/spotify/connection`
- `GET /api/users/{appUserId}/top-song?period=DAY|WEEK|MONTH|YEAR`
- `GET /api/users/{appUserId}/song-of-the-day` as a compatibility alias for `period=DAY`
- `GET /api/users/{appUserId}/our-song?otherUserId={otherUserId}&period=DAY|WEEK|MONTH|YEAR`

The backend does not create users locally. It expects `{appUserId}` to come from your upstream account system.

User-scoped routes are protected by a signed upstream auth token. The upstream backend is expected to:

- verify its own user/session auth boundary
- resolve the canonical persisted `app_user_id`
- mint a short-lived JWT for a specific `app_user_id`
- send it in the `Authorization: Bearer {jwt}` header for server-to-server reads
- append it as the `upstreamAuth` query parameter for browser redirects to `/spotify/connect`

For local development, the tracked `dev` profile disables that upstream JWT requirement so you can test the API without a second backend process. Use manual JWT generation only when you want to test the real auth contract locally.

Useful operational endpoints:

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /docs`
- `GET /openapi`

Production profile note:

- use `prod` for production runtime defaults
- `prod` automatically activates `container`, so structured JSON console logging remains enabled
- `prod` also enables strict startup validation for required Spotify, crypto, redirect, and upstream JWT settings
- `prod` defaults `server.forward-headers-strategy` to `framework` so trusted proxy headers are honored

## Deployment Note

Running this service as an internal-only Kubernetes service is the right default for the read endpoints.

Important OAuth caveat:

- the Spotify connect flow and `/api/spotify/callback` still need a browser-visible route that Spotify can redirect to
- that can be a direct ingress to this service or a path proxied through your main app/backend
- the service does not need to be generally public as a separate internet-facing API if your upstream app is handling the external edge
- if you proxy through an ingress or upstream backend, forward standard `Forwarded` or `X-Forwarded-*` headers consistently

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

- generate a short-lived upstream auth JWT for the target UUID
- open `http://127.0.0.1:8080/api/users/{appUserId}/spotify/connect?upstreamAuth={token}` in a browser
- complete Spotify auth
- inspect the linked account with `Authorization: Bearer {token}` at `http://127.0.0.1:8080/api/users/{appUserId}/spotify/connection`
- unlink the Spotify account with `Authorization: Bearer {token}` at `DELETE http://127.0.0.1:8080/api/users/{appUserId}/spotify/connection`
- read the top song with `Authorization: Bearer {token}` at `http://127.0.0.1:8080/api/users/{appUserId}/top-song?period=DAY`
- the older daily alias still works at `http://127.0.0.1:8080/api/users/{appUserId}/song-of-the-day`
- read the shared song with `Authorization: Bearer {token}` at `http://127.0.0.1:8080/api/users/{appUserId}/our-song?otherUserId={otherUserId}&period=DAY`

If you linked accounts before the `app_user_id` migration, re-run the connect flow through the user-scoped URL so the existing `spotify_account` row is attached to the correct UUID.

## Next Recommended Work

1. Finalize the token-minting contract on the upstream backend that will call this service.
2. Decide whether pairwise reads should support explicit date selection in addition to current-period reads.
3. Add cross-pod poller coordination before scaling beyond one replica.
4. Add operational dashboards around polling success, lag, and reauthorization status.
5. Decide whether the legacy `/song-of-the-day` alias should stay long-term or be retired after frontend adoption of `/top-song`.
