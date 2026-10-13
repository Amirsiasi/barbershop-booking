-- WhatsApp booking requests (phase 1).
-- A request arrives by chat, reserves the slot as PENDING and waits for the barber to confirm.

-- 1) New status PENDING. It blocks the barber's time exactly like a confirmed appointment,
--    so nobody can book the slot online while the barber decides.
ALTER TABLE appointment DROP CONSTRAINT appointment_status_check;
ALTER TABLE appointment ADD CONSTRAINT appointment_status_check
    CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'));

ALTER TABLE appointment DROP CONSTRAINT no_double_booking;
ALTER TABLE appointment ADD CONSTRAINT no_double_booking EXCLUDE USING gist (
    barber_id WITH =,
    tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (status IN ('PENDING', 'CONFIRMED', 'COMPLETED'));

-- 2) Where the booking came from, and until when an unconfirmed request holds the slot.
ALTER TABLE appointment ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'ONLINE'
    CHECK (source IN ('ONLINE', 'WHATSAPP', 'ADMIN'));
ALTER TABLE appointment ADD COLUMN pending_until TIMESTAMPTZ;

CREATE INDEX idx_appointment_pending ON appointment (pending_until) WHERE status = 'PENDING';

-- 3) Conversation state per chat partner: what we already know while we ask follow-up questions
--    ("Welche Leistung?" -> "1").
CREATE TABLE chat_session (
    id              BIGSERIAL PRIMARY KEY,
    shop_id         BIGINT       NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    phone           VARCHAR(30)  NOT NULL,
    state           VARCHAR(30)  NOT NULL DEFAULT 'IDLE',
    customer_name   VARCHAR(100),
    requested_date  DATE,
    requested_time  TIME,
    service_id      BIGINT       REFERENCES service_offering (id),
    barber_id       BIGINT       REFERENCES barber (id),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (shop_id, phone)
);

-- 4) Every incoming and outgoing chat message, so the barber can read the conversation.
CREATE TABLE chat_message (
    id          BIGSERIAL PRIMARY KEY,
    shop_id     BIGINT       NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    phone       VARCHAR(30)  NOT NULL,
    direction   VARCHAR(3)   NOT NULL CHECK (direction IN ('IN', 'OUT')),
    text        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_message_phone ON chat_message (shop_id, phone, created_at);
