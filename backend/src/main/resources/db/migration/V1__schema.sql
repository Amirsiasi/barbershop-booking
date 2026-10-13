-- BarberBook schema
-- Every business table carries shop_id, so one installation can serve many shops.
-- All points in time are stored as TIMESTAMPTZ (UTC); conversion to the shop's
-- time zone (e.g. Europe/Vienna) happens only when showing them.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE shop (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(60)  NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    tagline     VARCHAR(200),
    street      VARCHAR(120) NOT NULL,
    zip_city    VARCHAR(80)  NOT NULL,
    phone       VARCHAR(40),
    email       VARCHAR(120),
    time_zone   VARCHAR(40)  NOT NULL DEFAULT 'Europe/Vienna',
    highlights  TEXT,                      -- one highlight per line
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE opening_hours (
    id           BIGSERIAL PRIMARY KEY,
    shop_id      BIGINT   NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    day_of_week  INT      NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),  -- ISO: 1 = Monday
    open_time    TIME     NOT NULL,
    close_time   TIME     NOT NULL,
    CHECK (close_time > open_time),
    UNIQUE (shop_id, day_of_week)
);

CREATE TABLE barber (
    id          BIGSERIAL PRIMARY KEY,
    shop_id     BIGINT       NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    name        VARCHAR(80)  NOT NULL,
    bio         VARCHAR(300),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0
);

CREATE TABLE service_offering (
    id                BIGSERIAL PRIMARY KEY,
    shop_id           BIGINT       NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    category          VARCHAR(80)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    description       VARCHAR(300),
    duration_minutes  INT          NOT NULL CHECK (duration_minutes BETWEEN 5 AND 480),
    price_cents       INT          NOT NULL CHECK (price_cents >= 0),
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order        INT          NOT NULL DEFAULT 0
);

CREATE TABLE customer (
    id          BIGSERIAL PRIMARY KEY,
    shop_id     BIGINT       NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    first_name  VARCHAR(50)  NOT NULL,
    last_name   VARCHAR(50)  NOT NULL,
    phone       VARCHAR(30)  NOT NULL,     -- normalised: digits and leading +
    email       VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (shop_id, phone)
);

CREATE TABLE appointment (
    id                BIGSERIAL PRIMARY KEY,
    shop_id           BIGINT       NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    barber_id         BIGINT       NOT NULL REFERENCES barber (id),
    service_id        BIGINT       NOT NULL REFERENCES service_offering (id),
    customer_id       BIGINT       NOT NULL REFERENCES customer (id),
    starts_at         TIMESTAMPTZ  NOT NULL,
    ends_at           TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED'
                      CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    manage_token      UUID         NOT NULL UNIQUE,
    note              VARCHAR(300),
    price_cents       INT          NOT NULL,        -- price at booking time
    reminder_sent_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at),

    -- The heart of the system: the DATABASE guarantees that one barber can never
    -- have two overlapping active appointments, even if two customers press
    -- "book" in the same millisecond. '[)' = end is exclusive, so 10:00-10:35
    -- and 10:35-11:10 do not collide.
    CONSTRAINT no_double_booking EXCLUDE USING gist (
        barber_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('CONFIRMED', 'COMPLETED'))
);

CREATE INDEX idx_appointment_shop_start ON appointment (shop_id, starts_at);
CREATE INDEX idx_appointment_reminder   ON appointment (starts_at) WHERE status = 'CONFIRMED' AND reminder_sent_at IS NULL;

-- Breaks, holidays, sick days: time in which a barber cannot be booked.
CREATE TABLE barber_absence (
    id         BIGSERIAL PRIMARY KEY,
    barber_id  BIGINT       NOT NULL REFERENCES barber (id) ON DELETE CASCADE,
    starts_at  TIMESTAMPTZ  NOT NULL,
    ends_at    TIMESTAMPTZ  NOT NULL,
    reason     VARCHAR(120),
    CHECK (ends_at > starts_at)
);

CREATE INDEX idx_absence_barber_start ON barber_absence (barber_id, starts_at);
