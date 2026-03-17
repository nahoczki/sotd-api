# Stack Alignment Implementation Report

Date: 2026-03-17

## Scope

This report summarizes the first implementation pass that aligned the project with:

- [sotd-arch-report.md](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\ai-reports\sotd-arch-report.md)
- [tech-stack-recommendation-report.md](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\ai-reports\tech-stack-recommendation-report.md)

## Summary

The application has been refactored to match the recommended baseline stack:

- Spring Boot kept as the application framework
- Java toolchain changed from `25` to `21`
- MySQL replaced with PostgreSQL in the project baseline
- Flyway retained and now backed by an initial PostgreSQL migration
- Spring MVC, JDBC, validation, and actuator support added
- custom shallow source roots enabled in Gradle

## Source Layout Refactor

The source layout was flattened away from the default JetBrains structure.

New active source roots:

- `src/main`
- `src/test`

New application package root:

- `sotd`

Important implementation note:

- I intentionally used a shallow `sotd` package under `src/main/sotd` rather than Java's default package.
- That keeps the file structure much lighter than `src/main/java/com/example/...` while avoiding the classpath and component-scanning problems that come with a default-package Spring Boot application.

Current bootstrap entrypoint:

- [SotdApiApplication.java](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\sotd\SotdApiApplication.java)

## Changes Applied

### Build and dependency updates

Updated [build.gradle](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\build.gradle) to:

- use Java `21`
- compile Java from `src/main` and `src/test`
- add `spring-boot-starter-web`
- add `spring-boot-starter-jdbc`
- add `spring-boot-starter-validation`
- add `spring-boot-starter-actuator`
- keep `spring-boot-starter-flyway`
- replace MySQL driver/module usage with PostgreSQL
- add the Spring Boot configuration processor
- use `spring-boot-starter-test` for testing

### Database and local runtime updates

Updated [compose.yaml](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\compose.yaml) to:

- run PostgreSQL `18`
- expose port `5432`
- use a persistent Docker volume
- add a simple healthcheck

Updated [application.properties](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\resources\application.properties) to:

- point the datasource at PostgreSQL
- configure Flyway migration discovery
- expose actuator health/info endpoints
- add initial Spotify base/configuration properties
- add polling interval properties for the future ingestion layer

### Application bootstrap and config

Added:

- [SotdApiApplication.java](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\sotd\SotdApiApplication.java)
- [SchedulingConfig.java](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\sotd\config\SchedulingConfig.java)
- [AppClockConfig.java](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\sotd\config\AppClockConfig.java)
- [SpotifyHttpConfig.java](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\sotd\config\SpotifyHttpConfig.java)
- [SpotifyProperties.java](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\sotd\spotify\SpotifyProperties.java)

This gives the app:

- scheduling support via `@EnableScheduling`
- an application `Clock` bean
- `ThreadPoolTaskScheduler`
- explicit `RestClient` infrastructure for Spotify API and accounts calls
- externalized Spotify configuration binding

### Initial API scaffold

Added:

- [SongOfDayController.java](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\sotd\api\SongOfDayController.java)
- [SongOfDayService.java](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\sotd\song\SongOfDayService.java)
- [SongOfDayResponse.java](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\sotd\song\SongOfDayResponse.java)

This is still a stub. It provides the initial endpoint shape without pretending the Spotify ingestion/query path is finished yet.

### Initial Flyway schema

Added:

- [V1__create_sotd_core_schema.sql](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\main\resources\db\migration\V1__create_sotd_core_schema.sql)

The initial schema includes:

- `spotify_account`
- `spotify_track`
- `spotify_artist`
- `spotify_track_artist`
- `playback_event`
- `song_period_rollup`
- `song_period_winner`

This matches the earlier architecture recommendations and establishes the PostgreSQL-first DB model.

### Test refactor

Replaced the old generated test with:

- [SotdApiApplicationTests.java](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\src\test\sotd\SotdApiApplicationTests.java)

The test now targets the new package/source layout and excludes live DB auto-configuration so the basic context test remains fast and deterministic during this scaffolding phase.

## Verification

Executed:

```powershell
.\gradlew.bat test
```

Result:

- build successful
- tests passed

## Architectural Decisions Locked In

This pass locks in the following baseline decisions:

- keep Spring Boot instead of changing language/framework families
- use PostgreSQL instead of MySQL
- keep Flyway for schema evolution
- use a shallow `sotd` package structure instead of the deeper `com.example...` layout
- keep the app SQL-first and ready for `JdbcClient`-based repository work

## What Is Still Not Implemented

This pass intentionally does not yet implement the full Spotify workflow.

Still pending:

- Spotify Authorization Code flow
- secure refresh-token encryption/decryption
- Spotify account linking and callback handling
- recently-played ingestion worker
- playback polling scheduler jobs
- JDBC repositories and SQL queries
- rollup computation and winner selection logic
- real `/api/song-of-the-day` data reads from PostgreSQL
- integration tests against a real PostgreSQL instance

## Recommended Next Pass

The next implementation pass should focus on backend foundations, in this order:

1. Add JDBC repositories for `spotify_account` and the playback tables.
2. Implement the Spotify connect/callback endpoints and token storage.
3. Implement the recently-played ingestion service with idempotent writes.
4. Add the first daily rollup job and replace the SOTD stub with a DB-backed query.
5. Add PostgreSQL-backed integration tests, preferably with Testcontainers.

## Final Assessment

The repo is now aligned with the recommended tech stack and is in a materially better starting state for the actual Spotify/OAuth/data-ingestion work.

The most important outcome is that the app now has:

- the right framework baseline
- the right database baseline
- the right migration baseline
- a shallow but stable source layout
- a verified buildable starting point for the next implementation phase
