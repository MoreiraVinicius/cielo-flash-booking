ALTER TABLE notification_delivery
    DROP CONSTRAINT notification_delivery_status_check;

ALTER TABLE notification_delivery
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD CONSTRAINT notification_delivery_status_check
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    ADD CONSTRAINT notification_delivery_lease_check
        CHECK ((status = 'SENDING' AND lease_until IS NOT NULL)
            OR (status <> 'SENDING' AND lease_until IS NULL));

CREATE INDEX notification_delivery_terminal_updated_at_idx
    ON notification_delivery (updated_at)
    WHERE status IN ('SENT', 'FAILED');

CREATE INDEX outbox_event_published_at_idx
    ON outbox_event (published_at)
    WHERE published_at IS NOT NULL;
