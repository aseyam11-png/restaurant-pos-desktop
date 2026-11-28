CREATE TABLE app_installation (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    initialized_at TIMESTAMPTZ
);
INSERT INTO app_installation(singleton_id, initialized_at) VALUES (1, NULL);

CREATE TABLE local_users (
    id UUID PRIMARY KEY,
    username VARCHAR(40) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'CASHIER')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts SMALLINT NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_local_users_username_lower ON local_users(lower(username));
CREATE INDEX ix_local_users_active_role ON local_users(active, role);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES local_users(id) ON DELETE RESTRICT,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_user_sessions_user_expiry ON user_sessions(user_id, expires_at DESC);
CREATE INDEX ix_user_sessions_active_expiry ON user_sessions(expires_at) WHERE revoked_at IS NULL;
