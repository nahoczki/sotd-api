alter table spotify_account
    alter column refresh_token_encrypted drop not null;

alter table spotify_account
    add column disconnected_at timestamptz;
