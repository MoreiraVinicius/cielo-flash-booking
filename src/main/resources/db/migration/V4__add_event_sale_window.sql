ALTER TABLE event
    ADD COLUMN starts_at TIMESTAMPTZ,
    ADD COLUMN ends_at TIMESTAMPTZ;

ALTER TABLE event
    ADD CONSTRAINT event_starts_after_creation_check
        CHECK (starts_at IS NULL OR starts_at > created_at),
    ADD CONSTRAINT event_sale_window_check
        CHECK (
            ends_at IS NULL
            OR (starts_at IS NOT NULL AND ends_at > starts_at)
            OR (starts_at IS NULL AND ends_at >= created_at + INTERVAL '10 minutes')
        );
