# SOTD Tech Stack Recommendation Report

Date: 2026-03-17

## Inputs

This recommendation is based on:

- [sotd-arch-report.md](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\ai-reports\sotd-arch-report.md)
- the current repo baseline in [build.gradle](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\build.gradle)
- the current local DB setup in [compose.yaml](C:\Users\Luke Ryktarsyk\IdeaProjects\sotd-api\compose.yaml)

## Executive Recommendation

Java and Spring Boot are already close to optimal for this project. I would keep the stack in that family.

Recommended stack:

- Java `21` LTS
- Spring Boot `4.0.x`
- Spring MVC for inbound HTTP endpoints
- Spring `RestClient` for Spotify API calls
- Spring scheduling with `@Scheduled` and `ThreadPoolTaskScheduler`
- PostgreSQL `18` as the primary database
- Flyway for schema evolution
- `JdbcClient` as the primary SQL access layer
- HikariCP via Spring Boot defaults
- Spring Boot Actuator for health and job visibility

Primary changes from the current repo:

- keep Spring Boot
- switch the database target from MySQL to PostgreSQL
- keep Flyway
- add web, JDBC, validation, scheduling, and actuator support
- do not build this around JPA/Hibernate

## Final Call

### Keep Java and Spring

Verdict: yes, keep Java and Spring.

Reasoning:

- This service is a long-running backend with OAuth, scheduled polling, token refresh, and SQL-heavy rollups.
- Spring Boot already gives you strong support for HTTP endpoints, outbound REST calls, scheduling, JDBC, configuration, and operational hooks.
- Rewriting to Node, Python, or Go would not solve the hard parts of this app. The hard parts are data modeling, idempotent polling, token management, and SQL design.

### Change the database

Verdict: switch from MySQL to PostgreSQL.

Reasoning:

- This project is read-model and aggregation heavy, not just CRUD-heavy.
- PostgreSQL gives you native `jsonb`, declarative partitioning, generated columns, and materialized views.
- Those features map cleanly to raw Spotify payload storage, future event-table growth, and fast reporting patterns.

### Keep Flyway

Verdict: yes, keep Flyway and use it from day one.

Reasoning:

- The schema will evolve quickly while you discover the right event and rollup model.
- Flyway is well suited to versioned DDL, indexes, SQL functions, and view definitions.
- It is not the right tool for recurring app data refreshes, polling, or rollup recomputation jobs.

## Recommended Stack by Layer

### 1. Runtime and framework

Recommendation:

- Spring Boot `4.0.x`
- Java `21` LTS preferred over `25`

Reasoning:

- Spring Boot `4.0.3` officially supports Java `17` through `25`, so the current project is valid as-is.
- My recommendation to prefer Java `21` is an engineering inference, not a Spring requirement.
- This app does not need Java `25`-specific features, and Java `21` is the more conservative default for tooling compatibility and long-lived maintenance.

If you want the smallest possible change right now:

- keep Spring Boot `4.0.3`
- drop the toolchain from Java `25` to Java `21`

### 2. Inbound API layer

Recommendation:

- Spring MVC via `spring-boot-starter-web`

Reasoning:

- The inbound workload is a small REST API, not a massive streaming or websocket platform.
- The service is naturally JDBC-backed and scheduling-backed, which already pushes you toward a blocking model.
- WebFlux on the server side would add complexity without solving a current bottleneck.

### 3. Outbound Spotify client

Recommendation:

- Spring `RestClient`

Reasoning:

- Spring Boot's current reference docs explicitly recommend `RestClient` when you are not using WebFlux or Project Reactor.
- Your Spotify integration is straightforward request/response traffic with low concurrency.
- `RestClient` keeps the code imperative and easier to debug than a reactive chain.

Alternative:

- `WebClient` is also valid, but I would not choose it first for this app.

### 4. Scheduling and polling

Recommendation:

- `@EnableScheduling`
- `@Scheduled`
- explicit `ThreadPoolTaskScheduler`

Reasoning:

- Spring Framework has first-class scheduling support.
- `ThreadPoolTaskScheduler` is enough for a single-instance private app and gives you normal lifecycle management.
- Quartz is unnecessary unless you later need durable persisted jobs, clustered scheduling, or calendar-grade job control.

What not to add yet:

- Quartz
- Kafka
- RabbitMQ
- a separate worker service

For this POC, one Spring Boot process can host both the API and the polling jobs.

### 5. Database

Recommendation:

- PostgreSQL `18`

Why PostgreSQL fits better than MySQL for this app:

- `jsonb` is a strong fit for optional `raw_payload_json`
- declarative partitioning is available if `playback_event` grows large
- materialized views are available if you later want DB-managed summarized reads
- the SQL feature set is stronger for analytics-style rollups and maintenance jobs

Specific PostgreSQL features relevant here:

- `jsonb` supports containment and existence operators
- materialized views can be refreshed with `REFRESH MATERIALIZED VIEW`
- partitioning supports range partitioning, which is the natural fit for `played_at_utc`

### 6. SQL access layer

Primary recommendation:

- Spring `JdbcClient`

Why:

- This app is SQL-first, not object-graph-first.
- The number of write paths is small and explicit.
- Aggregation queries, upserts, and rollups are clearer in SQL than in JPA/Hibernate abstractions.
- `JdbcClient` is already supported directly by Spring Boot and keeps the stack light.

Not recommended as the default:

- JPA/Hibernate

Why not JPA:

- your main complexity is reporting queries, not entity relationships
- rollups, period winners, tie-breaking, and future DB optimizations are naturally SQL-driven
- JPA adds an object-mapping layer that does not solve your hardest problems

Optional upgrade path:

- jOOQ if query complexity expands materially

When I would adopt jOOQ:

- if SQL strings begin to sprawl across many repositories
- if you want generated type-safe references to tables and columns
- if you start leaning harder into PostgreSQL-specific SQL

For this repo today, `JdbcClient` is the simpler and more suitable starting point.

### 7. Schema migration layer

Recommendation:

- keep Flyway
- use SQL migrations as the default

How Flyway should be used here:

- versioned migrations for tables, constraints, indexes, enum types, and seed/reference rows
- repeatable migrations for views or helper SQL functions that may be redefined over time
- migration validation in local/dev/test so schema drift fails fast

How Flyway should not be used here:

- not for hourly polling
- not for daily rollup refreshes
- not for mutable application data
- not for "backfill every startup" behavior

This boundary matters:

- Flyway manages database structure and deterministic migration steps
- application jobs manage listening data ingestion and rollup computation

## PostgreSQL-Specific Design Guidance

### Use normal tables for winners and rollups first

Recommendation:

- `playback_event`
- `song_period_rollup`
- `song_period_winner`

Reasoning:

- Your API wants near-current answers after each ingestion cycle.
- PostgreSQL materialized views are fast to read, but they are refreshed explicitly and are therefore not always current.
- App-maintained rollup tables are a better fit for "update after each poll, read instantly from API."

Materialized views are still useful later if:

- you add heavier leaderboard or chart-style reporting
- refresh latency of minutes is acceptable

### Do not partition on day one

Recommendation:

- start with a normal `playback_event` table
- design it so it can later be partitioned by `played_at_utc` month

Reasoning:

- PostgreSQL docs are clear that partitioning pays off mainly when the table becomes very large.
- For a personal private app, you probably do not need partitioning immediately.
- Still, your event table shape should not block future range partitioning.

### Store local-day values explicitly

Recommendation:

- persist `played_date_local` and `period_start_local` as ordinary columns

Reasoning:

- PostgreSQL generated columns are useful, but they are not a good fit for everything here.
- Generated columns cannot be part of a partition key.
- Since SOTD logic depends on local day boundaries and future indexing or partitioning may matter, storing the derived local-day value explicitly is the safer choice.

### Use `jsonb` only where it buys you something

Recommendation:

- keep first-class columns for query-critical fields
- store raw Spotify payloads in `jsonb` only as an optional audit/debug field

Reasoning:

- the endpoint should query typed columns, not parse JSON at read time
- `jsonb` is useful for debugging, replay, and forward compatibility
- it should not become the primary query model

## Components I Do Not Recommend

### Not recommended now

- JPA/Hibernate
- Spring Data JPA repositories
- WebFlux server stack
- R2DBC
- Redis
- Kafka
- RabbitMQ
- Quartz
- Elasticsearch

Reasoning:

- each of those adds real complexity
- none of them addresses the core problems of this private POC better than Spring MVC + JDBC + PostgreSQL

## Suggested Dependency Direction

Recommended baseline dependencies:

- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-jdbc`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.springframework.boot:spring-boot-starter-flyway`
- `org.postgresql:postgresql`
- `org.springframework.boot:spring-boot-docker-compose`
- test support appropriate for JDBC and PostgreSQL

Keep optional:

- `spring-boot-devtools`

Remove or replace:

- MySQL JDBC driver
- Flyway MySQL module
- MySQL container in `compose.yaml`

## Concrete Repo Recommendation

If I were standardizing this repo before implementation, I would do this:

1. Keep Spring Boot.
2. Change Java toolchain from `25` to `21`.
3. Replace MySQL with PostgreSQL in Gradle and Docker Compose.
4. Add `starter-web`, `starter-jdbc`, `starter-validation`, and `starter-actuator`.
5. Keep Flyway and start with SQL migrations under `src/main/resources/db/migration`.
6. Use `JdbcClient` repositories for token storage, playback ingestion, and SOTD queries.
7. Use Spring scheduling for polling and rollup maintenance.

## Flyway Recommendation in Detail

Flyway is useful here for three separate reasons:

### 1. Schema history and audit trail

Flyway maintains a schema history table that records what ran, when it ran, and whether checksums still match. That is valuable even for a private app because this schema is going to change often at the start.

### 2. Safe evolution of rollup structures

This project will likely iterate through:

- raw event tables
- winner-table refinements
- new indexes
- SQL helper functions
- optional views or materialized views

Flyway is exactly the right tool for that.

### 3. Repeatable DB objects

If you later add:

- SQL views
- helper functions
- stable reporting views

repeatable Flyway migrations are a good fit because they are re-applied when their checksum changes.

What Flyway should not own:

- Spotify token refresh logic
- periodic poll execution
- rollup recomputation cadence
- mutable user/event data workflows

## Source-Backed Notes

The following points come directly from current official documentation:

- Spring Boot `4.0.3` supports Java `17` through `25`.
- Spring Boot recommends `RestClient` for remote REST calls when you are not using WebFlux or Project Reactor.
- Spring Framework provides annotation-based scheduling support, and `ThreadPoolTaskScheduler` is intended for local scheduler use.
- Spring Boot auto-configures `JdbcClient`, `JdbcTemplate`, and also supports jOOQ.
- PostgreSQL supports declarative partitioning, generated columns, `jsonb`, and materialized views.
- Flyway supports versioned and repeatable migrations and tracks them in a schema history table.

## Final Recommendation

For this project, the most suitable stack is:

- Java `21`
- Spring Boot `4.0.x`
- Spring MVC
- Spring `RestClient`
- Spring scheduling with `ThreadPoolTaskScheduler`
- PostgreSQL `18`
- Flyway
- `JdbcClient`

This is the best balance of:

- correctness
- low operational complexity
- direct SQL control
- future flexibility for week/month/year rollups
- minimal rewrite cost from the repo you already created

If the project grows beyond the current SQL surface area, the first upgrade I would consider is:

- add jOOQ on top of PostgreSQL and Flyway

I would not change the language or framework family before exhausting this stack.

## Sources

- Spring Boot system requirements  
  https://docs.spring.io/spring-boot/system-requirements.html
- Spring Boot REST client reference  
  https://docs.spring.io/spring-boot/reference/io/rest-client.html
- Spring Boot SQL data access reference  
  https://docs.spring.io/spring-boot/reference/data/sql.html
- Spring Framework scheduling reference  
  https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- PostgreSQL partitioning  
  https://www.postgresql.org/docs/current/ddl-partitioning.html
- PostgreSQL generated columns  
  https://www.postgresql.org/docs/current/ddl-generated-columns.html
- PostgreSQL JSON types  
  https://www.postgresql.org/docs/current/datatype-json.html
- PostgreSQL materialized views  
  https://www.postgresql.org/docs/current/rules-materializedviews.html
- Redgate Flyway migrations  
  https://documentation.red-gate.com/fd/migrations-271585107.html
- Redgate Flyway schema history table  
  https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table
- Redgate Flyway repeatable migrations  
  https://documentation.red-gate.com/fd/repeatable-migrations-273973335.html
