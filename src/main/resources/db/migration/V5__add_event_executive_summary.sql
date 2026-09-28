CREATE TABLE executive_summary_control (
    id BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    enabled_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO executive_summary_control (id, enabled)
VALUES (TRUE, FALSE);

ALTER TABLE event
    ADD COLUMN first_available_zero_at TIMESTAMPTZ;

CREATE TABLE event_executive_summary (
    event_id UUID PRIMARY KEY REFERENCES event (id) ON DELETE RESTRICT,
    status TEXT NOT NULL CHECK (status IN ('PARTIAL', 'READY')),
    delivery_status TEXT NOT NULL CHECK (
        delivery_status IN ('NOT_CONFIGURED', 'SENT', 'FAILED', 'UNKNOWN')
    ),
    as_of TIMESTAMPTZ NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    delivery_confirmed_at TIMESTAMPTZ,
    markdown TEXT NOT NULL,
    accepted_reservations BIGINT NOT NULL DEFAULT 0 CHECK (accepted_reservations >= 0),
    accepted_tickets BIGINT NOT NULL DEFAULT 0 CHECK (accepted_tickets >= 0),
    valid_tickets_at_close BIGINT NOT NULL DEFAULT 0 CHECK (valid_tickets_at_close >= 0),
    cancelled_tickets BIGINT NOT NULL DEFAULT 0 CHECK (cancelled_tickets >= 0),
    expired_tickets BIGINT NOT NULL DEFAULT 0 CHECK (expired_tickets >= 0),
    peak_minute TIMESTAMPTZ,
    peak_tickets BIGINT CHECK (peak_tickets IS NULL OR peak_tickets >= 0),
    first_five_minute_tickets BIGINT CHECK (
        first_five_minute_tickets IS NULL OR first_five_minute_tickets >= 0
    ),
    first_available_zero_at TIMESTAMPTZ,
    model_id TEXT,
    input_tokens BIGINT CHECK (input_tokens IS NULL OR input_tokens >= 0),
    output_tokens BIGINT CHECK (output_tokens IS NULL OR output_tokens >= 0),
    error_code TEXT
);
