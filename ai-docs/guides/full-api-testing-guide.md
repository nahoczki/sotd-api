# Full API Testing Guide

Date: 2026-03-18

## Purpose

This guide covers the full current HTTP surface of the app:

- Swagger UI
- OpenAPI JSON
- actuator health/info/metrics
- Spotify connect and callback flow
- linked-account read
- unlink
- top-song
- our-song

It is written for the app's current state, not a future roadmap state.

## Current Endpoint Inventory

### Documentation

- `GET /docs`
- `GET /openapi`

### Actuator

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/metrics`
- `GET /actuator/metrics/{meterName}`

### Spotify auth / connection lifecycle

- `GET /api/users/{appUserId}/spotify/connect`
- `GET /api/spotify/callback`
- `GET /api/users/{appUserId}/spotify/connection`
- `DELETE /api/users/{appUserId}/spotify/connection`

### Music insight reads

- `GET /api/users/{appUserId}/top-song?period=DAY|WEEK|MONTH|YEAR`
- `GET /api/users/{appUserId}/our-song?otherUserId={otherUserId}&period=DAY|WEEK|MONTH|YEAR`

## Prerequisites

Before testing, make sure:

1. Docker Postgres is running.
2. The app starts cleanly.
3. Flyway migrations have applied.
4. Your `.env` is populated.
5. You have at least one real Spotify app configured for local redirect testing.

Start Postgres:

```powershell
docker compose up -d postgres
```

Run the app:

```powershell
.\gradlew.bat bootRun
```

Run the full automated suite:

```powershell
.\gradlew.bat fullSuite
```

## Recommended Local UUIDs

Use fixed placeholders when the upstream backend is not issuing real UUIDs yet.

Example:

- profile A: `11111111-1111-1111-1111-111111111111`
- profile B: `22222222-2222-2222-2222-222222222222`

## Auth Modes

### Mode 1: Simplest local testing

Use the tracked `local` profile alias:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

In IntelliJ's Active profiles field, enter:

```text
local
```

Equivalent behavior if you prefer env-based override:

```env
SOTD_UPSTREAM_AUTH_ENABLED=false
```

Effect:

- all user-scoped routes can be hit directly with no JWT
- best for quick endpoint development checks
- no dependency on the upstream backend process

### Mode 2: Realistic local testing

Set:

```env
SOTD_UPSTREAM_AUTH_ENABLED=true
SOTD_UPSTREAM_AUTH_SHARED_SECRET=your-secret
SOTD_UPSTREAM_AUTH_ISSUER=accounts-api
SOTD_UPSTREAM_AUTH_AUDIENCE=sotd-api
```

Effect:

- user-scoped read/delete routes require `Authorization: Bearer {jwt}`
- browser connect flow requires `?upstreamAuth={jwt}`
- still does not require the real upstream backend if you mint the JWTs manually

### Recommended choice

For starting this guide now:

- use `local` profile first

Use manual JWT generation later when you want to test the real upstream-auth contract.

### JWT generation for local testing

Use this PowerShell snippet to mint a short-lived `HS256` JWT for one UUID:

```powershell
$secret = "your-secret"
$issuer = "accounts-api"
$audience = "sotd-api"
$appUserId = "11111111-1111-1111-1111-111111111111"
$issuedAt = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$expiresAt = [DateTimeOffset]::UtcNow.AddMinutes(5).ToUnixTimeSeconds()
$headerJson = '{"alg":"HS256","typ":"JWT"}'
$payloadJson = "{`"iss`":`"$issuer`",`"aud`":`"$audience`",`"sub`":`"$appUserId`",`"iat`":$issuedAt,`"exp`":$expiresAt}"
$headerBytes = [System.Text.Encoding]::UTF8.GetBytes($headerJson)
$payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payloadJson)
$headerPart = [Convert]::ToBase64String($headerBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$payloadPart = [Convert]::ToBase64String($payloadBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$signingInput = "$headerPart.$payloadPart"
$signingBytes = [System.Text.Encoding]::UTF8.GetBytes($signingInput)
$hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($secret))
$signatureBytes = $hmac.ComputeHash($signingBytes)
$signaturePart = [Convert]::ToBase64String($signatureBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
"$headerPart.$payloadPart.$signaturePart"
```

Use the resulting token as:

```text
Authorization: Bearer {token}
```

or:

```text
?upstreamAuth={token}
```

## Recommended Tools

Use these tools for the corresponding tests:

- browser: `/docs`, Spotify connect flow
- Insomnia or Postman: all `GET` / `DELETE` API tests
- `psql`: schema/data verification
- PowerShell: JWT generation and quick endpoint checks

## Test Order

Recommended full sequence:

1. docs endpoints
2. actuator endpoints
3. actuator metrics
4. pre-link connection reads
5. Spotify connect flow
6. post-link connection read
7. polling/data verification
8. top-song
9. our-song
10. unlink
11. relink and gap preservation checks

## Endpoint-By-Endpoint Guide

## 1. Swagger UI

Request:

```text
GET http://127.0.0.1:8080/docs
```

Expected:

- `200`
- Swagger UI loads
- you can inspect the current route contracts

What to verify:

- Spotify connect route shows query auth
- user-scoped reads show bearer JWT auth
- unlink route is visible

Important local-mode note:

- Swagger reflects the production auth contract
- if you run with `local`, runtime auth enforcement is disabled even though Swagger still shows JWT or `upstreamAuth`
- for `/spotify/connect`, use a normal browser tab instead of Swagger UI whenever possible

## 2. OpenAPI JSON

Request:

```text
GET http://127.0.0.1:8080/openapi
```

Expected:

- `200`
- valid OpenAPI JSON

What to verify:

- `/api/users/{appUserId}/spotify/connect`
- `/api/users/{appUserId}/spotify/connection`
- `DELETE /api/users/{appUserId}/spotify/connection`
- `/api/users/{appUserId}/top-song`
- `/api/users/{appUserId}/our-song`

## 3. Actuator Health

Request:

```text
GET http://127.0.0.1:8080/actuator/health
```

Expected:

- `200`
- health JSON
- typically `"status":"UP"` if app and DB are healthy

## 4. Actuator Info

Request:

```text
GET http://127.0.0.1:8080/actuator/info
```

Expected:

- `200`
- non-empty JSON with operational metadata

What to verify:

- `app.name`
- `app.version`
- `build.version`
- `build.time`
- `docs.swaggerUiPath`
- `features.spotifyUnlink = true`
- `git.commitShortSha`
- `git.branch`
- `auth.upstreamJwtEnabled`
- `polling.recentlyPlayedInterval`

## 5. Actuator Metrics

Request:

```text
GET http://127.0.0.1:8080/actuator/metrics
```

Expected:

- `200`
- a list of available meter names

What to verify:

- `names` includes `sotd.spotify.callback.outcomes`
- `names` includes `sotd.spotify.poll.account.outcomes`
- `names` includes `sotd.spotify.accounts`

Example specific metric:

```text
GET http://127.0.0.1:8080/actuator/metrics/sotd.spotify.poll.account.outcomes
```

Expected:

- `200`
- meter measurements and available tags

Operational note:

- treat `/actuator/metrics` as an internal ops endpoint
- do not expose it on the public internet-facing edge

## 6. Linked Account Read Before Connect

### Auth disabled

```text
GET http://127.0.0.1:8080/api/users/11111111-1111-1111-1111-111111111111/spotify/connection
```

### Auth enabled

Same request, plus:

```text
Authorization: Bearer {token-for-profile-A}
```

Expected before linking:

- `404`

## 7. Start Spotify Connect Flow

This is a browser test, not a pure API-client test.

### Auth disabled

Open:

```text
http://127.0.0.1:8080/api/users/11111111-1111-1111-1111-111111111111/spotify/connect
```

### Auth enabled

Open:

```text
http://127.0.0.1:8080/api/users/11111111-1111-1111-1111-111111111111/spotify/connect?upstreamAuth={token-for-profile-A}
```

Expected:

- `302` to Spotify authorize
- Spotify login/consent page
- Spotify redirect back to `/api/spotify/callback`
- JSON success response from the callback

### Negative callback checks

You can manually test bad callback states:

```text
GET http://127.0.0.1:8080/api/spotify/callback
GET http://127.0.0.1:8080/api/spotify/callback?error=access_denied
```

Expected:

- `400`

## 8. Linked Account Read After Connect

Request:

```text
GET http://127.0.0.1:8080/api/users/11111111-1111-1111-1111-111111111111/spotify/connection
```

If auth enabled:

```text
Authorization: Bearer {token-for-profile-A}
```

Expected:

- `200`
- JSON with:
  - `appUserId`
  - `spotifyUserId`
  - `displayName`
  - `scope`
  - `status`
  - `timezone`
  - `accessTokenExpiresAt`

## 9. Top Song

Request:

```text
GET http://127.0.0.1:8080/api/users/11111111-1111-1111-1111-111111111111/top-song?period=DAY
```

If auth enabled:

```text
Authorization: Bearer {token-for-profile-A}
```

Expected states:

- `unlinked`
- `pending`
- `ready`

Supported periods:

- `DAY`
- `WEEK`
- `MONTH`
- `YEAR`

### `ready`

Expected when:

- user is linked
- polling has already ingested plays
- winner exists for the user's current local day

### `pending`

Expected when:

- user is linked
- no winner exists yet for the selected local period

### `unlinked`

Expected when:

- no Spotify account is linked for that `appUserId`

## 10. Our Song

Request shape:

```text
GET http://127.0.0.1:8080/api/users/11111111-1111-1111-1111-111111111111/our-song?otherUserId=22222222-2222-2222-2222-222222222222&period=DAY
```

If auth enabled:

```text
Authorization: Bearer {token-for-profile-A}
```

Supported periods:

- `DAY`
- `WEEK`
- `MONTH`
- `YEAR`

Expected states:

- `ready`
- `no-common-song`
- `unlinked`

### Important current rule

The comparison window is anchored to the first user's timezone.

So these may not resolve to the same period window:

- `/api/users/A/our-song?otherUserId=B&period=DAY`
- `/api/users/B/our-song?otherUserId=A&period=DAY`

### Negative check

Self-comparison should fail:

```text
GET /api/users/A/our-song?otherUserId=A&period=DAY
```

Expected:

- `400`

## 11. Unlink

Request:

```text
DELETE http://127.0.0.1:8080/api/users/11111111-1111-1111-1111-111111111111/spotify/connection
```

If auth enabled:

```text
Authorization: Bearer {token-for-profile-A}
```

Expected:

- `204`

Expected follow-up behavior:

- `GET /spotify/connection` -> `404`
- `GET /top-song?period=DAY` -> `unlinked`
- `GET /our-song?...` -> `unlinked` if that user is the requester or comparison target and no longer linked

### Idempotency check

Send the same delete twice.

Expected:

- `204` both times

## 12. Relink After Unlink

Use the browser connect flow again for the same `appUserId`.

Expected:

- same Spotify account reconnects successfully
- old history becomes visible again
- polling resumes
- disconnected listening gap is preserved rather than backfilled

## Database Verification

## 1. Linked accounts

```sql
select
    id,
    app_user_id,
    spotify_user_id,
    display_name,
    status,
    disconnected_at,
    last_recently_played_cursor_ms,
    last_successful_poll_at
from spotify_account
order by updated_at desc;
```

Use this to verify:

- linked rows have `app_user_id`
- disconnected rows have `app_user_id = null`
- disconnected rows have `status = 'DISCONNECTED'`

## 2. Raw playback ingestion

```sql
with artist_names as (
    select
        sta.spotify_track_id,
        string_agg(sa.name, ', ' order by sta.artist_order) as artist_names
    from spotify_track_artist sta
    join spotify_artist sa on sa.spotify_artist_id = sta.spotify_artist_id
    group by sta.spotify_track_id
)
select
    sa.app_user_id,
    sa.spotify_user_id,
    pe.played_date_local,
    pe.played_at_utc,
    st.name as track_name,
    an.artist_names
from playback_event pe
join spotify_account sa on sa.id = pe.spotify_account_id
join spotify_track st on st.spotify_track_id = pe.spotify_track_id
left join artist_names an on an.spotify_track_id = st.spotify_track_id
order by pe.played_at_utc desc
limit 50;
```

## 3. Daily rollups

```sql
with artist_names as (
    select
        sta.spotify_track_id,
        string_agg(sa.name, ', ' order by sta.artist_order) as artist_names
    from spotify_track_artist sta
    join spotify_artist sa on sa.spotify_artist_id = sta.spotify_artist_id
    group by sta.spotify_track_id
)
select
    sa.app_user_id,
    spr.period_type,
    spr.period_start_local,
    st.name as track_name,
    an.artist_names,
    spr.play_count
from song_period_rollup spr
join spotify_account sa on sa.id = spr.spotify_account_id
join spotify_track st on st.spotify_track_id = spr.spotify_track_id
left join artist_names an on an.spotify_track_id = st.spotify_track_id
where spr.period_type = 'DAY'
order by spr.period_start_local desc, sa.app_user_id, spr.play_count desc;
```

## 4. Daily winners

```sql
with artist_names as (
    select
        sta.spotify_track_id,
        string_agg(sa.name, ', ' order by sta.artist_order) as artist_names
    from spotify_track_artist sta
    join spotify_artist sa on sa.spotify_artist_id = sta.spotify_artist_id
    group by sta.spotify_track_id
)
select
    sa.app_user_id,
    spw.period_start_local,
    st.name as track_name,
    an.artist_names,
    spw.play_count,
    spw.tie_break_rule
from song_period_winner spw
join spotify_account sa on sa.id = spw.spotify_account_id
join spotify_track st on st.spotify_track_id = spw.spotify_track_id
left join artist_names an on an.spotify_track_id = st.spotify_track_id
where spw.period_type = 'DAY'
order by spw.period_start_local desc, sa.app_user_id;
```

## 5. Our-song overlap check

```sql
with first_user as (
    select spr.spotify_track_id, sum(spr.play_count) as play_count
    from song_period_rollup spr
    join spotify_account sa on sa.id = spr.spotify_account_id
    where sa.app_user_id = '11111111-1111-1111-1111-111111111111'::uuid
      and spr.period_type = 'DAY'
      and spr.period_start_local = current_date
    group by spr.spotify_track_id
),
second_user as (
    select spr.spotify_track_id, sum(spr.play_count) as play_count
    from song_period_rollup spr
    join spotify_account sa on sa.id = spr.spotify_account_id
    where sa.app_user_id = '22222222-2222-2222-2222-222222222222'::uuid
      and spr.period_type = 'DAY'
      and spr.period_start_local = current_date
    group by spr.spotify_track_id
),
artist_names as (
    select
        sta.spotify_track_id,
        string_agg(sa.name, ', ' order by sta.artist_order) as artist_names
    from spotify_track_artist sta
    join spotify_artist sa on sa.spotify_artist_id = sta.spotify_artist_id
    group by sta.spotify_track_id
)
select
    fu.spotify_track_id,
    st.name as track_name,
    an.artist_names,
    fu.play_count as user_play_count,
    su.play_count as other_user_play_count,
    fu.play_count + su.play_count as combined_play_count
from first_user fu
join second_user su on su.spotify_track_id = fu.spotify_track_id
join spotify_track st on st.spotify_track_id = fu.spotify_track_id
left join artist_names an on an.spotify_track_id = fu.spotify_track_id
order by combined_play_count desc, least(fu.play_count, su.play_count) desc, fu.spotify_track_id asc;
```

## Full End-To-End Scenario

Use this as the most complete manual test:

1. Start Postgres.
2. Start the app.
3. Verify `/docs`, `/openapi`, and `/actuator/health`.
4. Check `GET /spotify/connection` for profile A before linking.
5. Link profile A in the browser.
6. Check `GET /spotify/connection` for profile A after linking.
7. Repeat steps 4 to 6 for profile B.
8. Wait for polling to ingest both accounts.
9. Verify `playback_event`, `song_period_rollup`, and `song_period_winner`.
10. Call `GET /top-song?period=DAY` for profile A.
11. Call `GET /top-song?period=MONTH` for profile B.
12. Call `GET /our-song?...` for `DAY`.
13. Call `GET /our-song?...` for `WEEK`.
14. Call `GET /our-song?...` for `YEAR`.
15. Call `DELETE /spotify/connection` for profile A.
16. Re-check profile A connection, top-song, and our-song results.
17. Reconnect profile A and confirm history returns without backfilling the disconnected gap.

## Troubleshooting

### `401 Unauthorized`

Check:

- `SOTD_UPSTREAM_AUTH_ENABLED`
- JWT signature
- JWT expiry
- issuer and audience values

### `403 Forbidden`

Check:

- JWT `sub` matches `{appUserId}`

### `404` on linked-account read after expected connect

Check:

- callback completed successfully
- `spotify_account.app_user_id` is populated

### `pending` on top-song

Check:

- polling ran recently
- `song_period_winner` has a row for that user/day

### `no-common-song` on our-song

Check:

- both users have data in `song_period_rollup`
- they actually overlap on at least one track in the selected period

### unlink appears to work but polling still runs

Check:

- `spotify_account.status = 'DISCONNECTED'`
- `spotify_account.refresh_token_encrypted is null`
- no stale row remains linked to `app_user_id`

## Related Docs

Specialized docs that still help for deeper dives:

- `ai-docs/guides/local-user-uuid-test-guide.md`
- `ai-docs/guides/local-postgres-setup-report.md`
- `ai-docs/guides/our-song-testing-guide.md`
- `ai-docs/guides/spotify-auth-test-plan.md`
