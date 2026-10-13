-- Personal logins for the team.
-- OWNER: everything (team, prices, logins). BARBER: sees the whole calendar, changes only own appointments.
-- The login from the environment (ADMIN_USERNAME / ADMIN_PASSWORD) stays as an emergency owner login.

CREATE TABLE staff_user (
    id                    BIGSERIAL PRIMARY KEY,
    shop_id               BIGINT       NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    barber_id             BIGINT       UNIQUE REFERENCES barber (id) ON DELETE SET NULL,
    username              VARCHAR(40)  NOT NULL UNIQUE,         -- always lower case
    password_hash         VARCHAR(100) NOT NULL,                -- BCrypt, never the password itself
    role                  VARCHAR(10)  NOT NULL CHECK (role IN ('OWNER', 'BARBER')),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,   -- start passwords are set by the owner
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
