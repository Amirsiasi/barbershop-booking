-- 1) Several services in one appointment ("Haarschnitt + Bart").
--    appointment.service_id stays as the first/main service; the full list with name, duration
--    and price as they were at booking time lives in appointment_item.
CREATE TABLE appointment_item (
    id               BIGSERIAL PRIMARY KEY,
    appointment_id   BIGINT       NOT NULL REFERENCES appointment (id) ON DELETE CASCADE,
    service_id       BIGINT       REFERENCES service_offering (id) ON DELETE SET NULL,
    position         INT          NOT NULL,
    name             VARCHAR(100) NOT NULL,
    duration_minutes INT          NOT NULL CHECK (duration_minutes > 0),
    price_cents      INT          NOT NULL CHECK (price_cents >= 0),
    UNIQUE (appointment_id, position)
);

CREATE INDEX appointment_item_appointment_idx ON appointment_item (appointment_id);

-- existing appointments get exactly one item
INSERT INTO appointment_item (appointment_id, service_id, position, name, duration_minutes, price_cents)
SELECT a.id, a.service_id, 0, s.name,
       GREATEST(1, (EXTRACT(EPOCH FROM (a.ends_at - a.starts_at)) / 60)::INT),
       a.price_cents
FROM appointment a
JOIN service_offering s ON s.id = a.service_id;

-- 2) Shop photo and rating for the booking summary card. The owner maintains both in the admin area.
ALTER TABLE shop
    ADD COLUMN rating       NUMERIC(2, 1) CHECK (rating BETWEEN 1 AND 5),
    ADD COLUMN review_count INT CHECK (review_count >= 0);

-- the picture sits in its own table, so loading the shop never drags the image bytes along
CREATE TABLE shop_photo (
    shop_id      BIGINT      PRIMARY KEY REFERENCES shop (id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL,
    data         BYTEA       NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3) WhatsApp chat: "1 und 3" -> several services. service_id keeps the first, the rest goes here ("4,7").
ALTER TABLE chat_session ADD COLUMN extra_service_ids VARCHAR(100);
