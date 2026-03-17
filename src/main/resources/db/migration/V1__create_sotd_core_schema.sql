create table spotify_account (
    id bigserial primary key,
    app_user_id text,
    spotify_user_id text not null,
    display_name text,
    scope text not null default '',
    refresh_token_encrypted bytea not null,
    access_token_expires_at timestamptz,
    last_refresh_at timestamptz,
    last_successful_poll_at timestamptz,
    last_recently_played_cursor_ms bigint,
    timezone text not null default 'UTC',
    status text not null default 'ACTIVE',
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint uq_spotify_account_spotify_user unique (spotify_user_id),
    constraint chk_spotify_account_status check (status in ('ACTIVE', 'REAUTH_REQUIRED', 'DISCONNECTED'))
);

create table spotify_track (
    spotify_track_id text primary key,
    name text not null,
    album_id text,
    album_name text,
    duration_ms integer not null,
    explicit boolean not null default false,
    external_url text,
    image_url text,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create table spotify_artist (
    spotify_artist_id text primary key,
    name text not null,
    external_url text,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create table spotify_track_artist (
    spotify_track_id text not null references spotify_track (spotify_track_id) on delete cascade,
    spotify_artist_id text not null references spotify_artist (spotify_artist_id) on delete cascade,
    artist_order smallint not null,
    created_at timestamptz not null default current_timestamp,
    primary key (spotify_track_id, spotify_artist_id),
    constraint uq_spotify_track_artist_order unique (spotify_track_id, artist_order)
);

create table playback_event (
    id bigserial primary key,
    spotify_account_id bigint not null references spotify_account (id) on delete cascade,
    spotify_track_id text not null references spotify_track (spotify_track_id),
    played_at_utc timestamptz not null,
    played_date_local date not null,
    source_context_type text,
    source_context_uri text,
    raw_payload_json jsonb,
    created_at timestamptz not null default current_timestamp,
    constraint uq_playback_event_account_track_played_at unique (spotify_account_id, spotify_track_id, played_at_utc)
);

create table song_period_rollup (
    id bigserial primary key,
    spotify_account_id bigint not null references spotify_account (id) on delete cascade,
    period_type text not null,
    period_start_local date not null,
    spotify_track_id text not null references spotify_track (spotify_track_id),
    play_count integer not null,
    total_duration_ms bigint not null,
    last_played_at_utc timestamptz not null,
    computed_at timestamptz not null default current_timestamp,
    constraint uq_song_period_rollup unique (spotify_account_id, period_type, period_start_local, spotify_track_id),
    constraint chk_song_period_rollup_period_type check (period_type in ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    constraint chk_song_period_rollup_play_count check (play_count >= 0),
    constraint chk_song_period_rollup_total_duration_ms check (total_duration_ms >= 0)
);

create table song_period_winner (
    id bigserial primary key,
    spotify_account_id bigint not null references spotify_account (id) on delete cascade,
    period_type text not null,
    period_start_local date not null,
    spotify_track_id text not null references spotify_track (spotify_track_id),
    play_count integer not null,
    tie_break_rule text not null,
    computed_at timestamptz not null default current_timestamp,
    constraint uq_song_period_winner unique (spotify_account_id, period_type, period_start_local),
    constraint chk_song_period_winner_period_type check (period_type in ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    constraint chk_song_period_winner_play_count check (play_count >= 0)
);

create index idx_playback_event_account_played_at
    on playback_event (spotify_account_id, played_at_utc desc);

create index idx_playback_event_account_local_day
    on playback_event (spotify_account_id, played_date_local);

create index idx_song_period_rollup_lookup
    on song_period_rollup (spotify_account_id, period_type, period_start_local, play_count desc, last_played_at_utc desc);

create index idx_song_period_winner_lookup
    on song_period_winner (spotify_account_id, period_type, period_start_local);
