# Local PostgreSQL Setup Report

Date: 2026-03-17

## Purpose

This document walks through the first-time local PostgreSQL setup for this project using Docker Desktop.

It matches the current repo configuration in:

- [compose.yaml](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\compose.yaml)
- [application.properties](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\resources\application.properties)

## Local Database Configuration

Current local connection settings:

- Host: `localhost`
- Port: `5432`
- Database: `sotd`
- Username: `sotd`
- Password: `sotd`

## First-Time Setup

### 1. Start PostgreSQL with Docker Compose

From the project root, run:

```powershell
docker compose up -d postgres
```

Notes:

- The first run may take a minute because Docker will pull `postgres:18-alpine`.
- The container uses a named Docker volume, so your DB data will persist between restarts.

### 2. Check that the container is running

Run:

```powershell
docker compose ps
```

You should see the `postgres` service listed and moving toward a healthy state.

If you want to watch startup logs:

```powershell
docker compose logs -f postgres
```

You want to see PostgreSQL finish startup and begin accepting connections.

### 3. Connect to the database

You can connect with any Postgres client using the values above.

For a built-in shell through the running container:

```powershell
docker compose exec postgres psql -U sotd -d sotd
```

Useful commands once inside `psql`:

```sql
\conninfo
\l
\dt
```

At first, the app tables may not exist yet. Flyway creates them when the Spring Boot app starts.

## Apply the Schema with Flyway

### 4. Start the Spring Boot app

From the project root, in a second terminal, run:

```powershell
.\gradlew.bat bootRun
```

What happens here:

- Spring Boot connects to local PostgreSQL
- Flyway runs automatically on startup
- the migration file [V1__create_sotd_core_schema.sql](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\resources\db\migration\V1__create_sotd_core_schema.sql) creates the initial schema

## Verify the Schema

### 5. Check that Flyway and the app tables exist

Inside `psql`, run:

```sql
\dt
select * from flyway_schema_history;
```

You should see:

- `flyway_schema_history`
- `spotify_account`
- `spotify_track`
- `spotify_artist`
- `spotify_track_artist`
- `playback_event`
- `song_period_rollup`
- `song_period_winner`

If you prefer a one-shot command from PowerShell:

```powershell
docker compose exec postgres psql -U sotd -d sotd -c '\dt'
```

## Verify the App Endpoint

### 6. Confirm the Spring app is reachable

With the app still running, test the stub endpoint:

```powershell
Invoke-RestMethod http://localhost:8080/api/song-of-the-day
```

At this stage, the endpoint should return the placeholder response from the current stub implementation.

## Stopping and Resetting

### Stop the container but keep the data

```powershell
docker compose down
```

### Destroy the local database and start fresh

```powershell
docker compose down -v
docker compose up -d postgres
```

Important:

- `docker compose down -v` deletes the local Postgres volume and all stored DB data.

## Quick Start Summary

For the shortest happy path:

1. `docker compose up -d postgres`
2. `.\gradlew.bat bootRun`
3. `docker compose exec postgres psql -U sotd -d sotd -c '\dt'`
4. `Invoke-RestMethod http://localhost:8080/api/song-of-the-day`

## Troubleshooting

### Port `5432` already in use

If Docker says port `5432` is busy, another local Postgres instance is already using it.

Options:

- stop the other Postgres instance
- or change the published port in [compose.yaml](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\compose.yaml) and update `spring.datasource.url` accordingly

### Flyway does not create tables

Check:

- that the container is running
- that the app started successfully
- that the DB credentials in [application.properties](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\resources\application.properties) still match the values in [compose.yaml](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\compose.yaml)

### Cannot connect with `psql`

First confirm the container is up:

```powershell
docker compose ps
```

Then inspect the logs:

```powershell
docker compose logs postgres
```
