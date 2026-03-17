# sotd-api

Private Spring Boot API for tracking Spotify listening history and surfacing a "song of the day" result backed by PostgreSQL.

## Current Status

This repository is in the scaffold phase.

Implemented now:

- Spring Boot 4 baseline
- Java 21 toolchain
- PostgreSQL local Docker setup
- Flyway migration baseline
- shallow source layout under `src/main/sotd`
- stub `GET /api/song-of-the-day` endpoint

Not implemented yet:

- Spotify Authorization Code flow
- token encryption and refresh handling
- Spotify polling/ingestion jobs
- DB-backed SOTD calculation

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
2. ingest listening history into PostgreSQL
3. compute daily, weekly, monthly, and yearly winners from stored play events
4. expose fast frontend-facing endpoints such as `song-of-the-day`

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
.\gradlew.bat test
```

### Hit the current endpoint

```powershell
Invoke-RestMethod http://localhost:8080/api/song-of-the-day
```

Right now this returns a placeholder response because the Spotify ingestion and rollup logic is still pending.

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

The Spotify app registration can be prepared now, but end-to-end auth testing is not complete until the backend OAuth endpoints are implemented.

The intended local callback URI is:

- `http://127.0.0.1:8080/api/spotify/callback`

## Reports

Detailed project notes live in:

- `ai-reports/sotd-arch-report.md`
- `ai-reports/tech-stack-recommendation-report.md`
- `ai-reports/stack-alignment-implementation-report.md`
- `ai-reports/local-postgres-setup-report.md`

## Next Recommended Work

1. Implement Spotify connect/callback endpoints.
2. Add secure refresh-token storage.
3. Build the recently-played ingestion service.
4. Add JDBC repositories and DB-backed SOTD queries.
5. Replace the stub endpoint with real rollup reads.
