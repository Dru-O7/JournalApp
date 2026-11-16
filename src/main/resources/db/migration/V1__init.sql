-- Users. Journal content is never stored here; account fields the server must read
-- (email, city) are encrypted at rest by the application (columns *_enc).
CREATE TABLE users (
    id              uuid PRIMARY KEY,
    username        varchar(32)  NOT NULL,
    -- bcrypt of the client-derived auth key; the raw password never reaches the server
    auth_hash       varchar(100) NOT NULL,
    -- the user's journal data key, encrypted (wrapped) in the browser with a password-derived key
    wrapped_key     varchar(200) NOT NULL,
    wrapped_key_iv  varchar(24)  NOT NULL,
    kdf_version     smallint     NOT NULL DEFAULT 1,
    email_enc       text,
    city_enc        text,
    weekly_report   boolean      NOT NULL DEFAULT false,
    -- bumped on credential change / "log out everywhere"; tokens carry it and are rejected when stale
    token_version   integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT users_username_format CHECK (username ~ '^[A-Za-z0-9_.-]{3,32}$'),
    CONSTRAINT users_weekly_report_needs_email CHECK (NOT weekly_report OR email_enc IS NOT NULL)
);
CREATE UNIQUE INDEX users_username_lower_uk ON users (lower(username));
CREATE INDEX users_weekly_report_idx ON users (id) WHERE weekly_report;

CREATE TABLE user_roles (
    user_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    varchar(16) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    PRIMARY KEY (user_id, role)
);

-- Journal entries. ciphertext = AES-GCM(JSON{title,content,sentiment}) produced in the browser
-- with a key the server never sees. Deleting a user deletes their entries.
CREATE TABLE journal_entries (
    id                   uuid PRIMARY KEY,
    user_id              uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ciphertext           text        NOT NULL CHECK (length(ciphertext) <= 200000),
    iv                   varchar(24) NOT NULL,
    enc_version          smallint    NOT NULL DEFAULT 1,
    -- only set when the owner opted in to the weekly mood email; encrypted with the server key
    report_sentiment_enc text,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    version              bigint      NOT NULL DEFAULT 0
);
CREATE INDEX journal_entries_user_created_idx ON journal_entries (user_id, created_at DESC);

-- ShedLock: ensures the weekly report runs on one instance only.
CREATE TABLE shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp    NOT NULL,
    locked_at  timestamp    NOT NULL,
    locked_by  varchar(255) NOT NULL
);
