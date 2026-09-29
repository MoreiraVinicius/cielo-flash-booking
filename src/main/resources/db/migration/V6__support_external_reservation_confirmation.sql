ALTER TABLE reservation
    ADD COLUMN confirmed_at TIMESTAMPTZ,
    ADD COLUMN cancellation_id UUID;

ALTER TABLE reservation
    DROP CONSTRAINT reservation_status_check,
    DROP CONSTRAINT reservation_check1;

ALTER TABLE reservation
    ADD CONSTRAINT reservation_status_check CHECK (
        status IN ('PENDING', 'CONFIRMED', 'CANCELLATION_PENDING', 'CANCELLED', 'EXPIRED')
    ),
    ADD CONSTRAINT reservation_lifecycle_metadata_check CHECK (
        CASE status
            WHEN 'PENDING' THEN
                confirmed_at IS NULL
                AND cancellation_id IS NULL
                AND closure_reason_code IS NULL
                AND closure_reason_description IS NULL
            WHEN 'CONFIRMED' THEN
                confirmed_at IS NOT NULL
                AND cancellation_id IS NULL
                AND closure_reason_code IS NULL
                AND closure_reason_description IS NULL
            WHEN 'CANCELLATION_PENDING' THEN
                confirmed_at IS NOT NULL
                AND cancellation_id IS NOT NULL
                AND closure_reason_code IS NULL
                AND closure_reason_description IS NULL
            WHEN 'CANCELLED' THEN
                closure_reason_code IS NOT DISTINCT FROM 'CANCELLED_BY_REQUEST'
                AND closure_reason_description IS NOT DISTINCT FROM 'Reserva cancelada por solicitação'
                AND (
                    (confirmed_at IS NULL AND cancellation_id IS NULL)
                    OR (confirmed_at IS NOT NULL AND cancellation_id IS NOT NULL)
                )
            WHEN 'EXPIRED' THEN
                confirmed_at IS NULL
                AND cancellation_id IS NULL
                AND closure_reason_code IS NOT DISTINCT FROM 'RESERVATION_DEADLINE_REACHED'
                AND closure_reason_description IS NOT DISTINCT FROM 'Prazo da reserva encerrado'
            ELSE FALSE
        END
    );

CREATE UNIQUE INDEX reservation_cancellation_id_uq
    ON reservation (cancellation_id)
    WHERE cancellation_id IS NOT NULL;

CREATE TABLE confirmation_inbox (
    source TEXT NOT NULL CHECK (char_length(btrim(source)) BETWEEN 1 AND 128),
    resolution_id TEXT NOT NULL CHECK (char_length(btrim(resolution_id)) BETWEEN 1 AND 128),
    reservation_id UUID REFERENCES reservation (id) ON DELETE RESTRICT,
    message_type TEXT NOT NULL CHECK (
        message_type IN ('ReservationConfirmationRequested', 'ReservationCancellationCompleted')
    ),
    payload_fingerprint TEXT NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    outcome JSONB NOT NULL CHECK (jsonb_typeof(outcome) = 'object'),
    cancellation_id UUID,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (source, resolution_id),
    CHECK (
        (message_type = 'ReservationConfirmationRequested' AND cancellation_id IS NULL)
        OR (message_type = 'ReservationCancellationCompleted' AND cancellation_id IS NOT NULL)
    )
);
