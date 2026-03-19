create table spotify_auth_state (
    state_token text primary key,
    app_user_id uuid not null,
    expires_at timestamptz not null,
    created_at timestamptz not null default current_timestamp
);

create index idx_spotify_auth_state_expires_at
    on spotify_auth_state (expires_at);
