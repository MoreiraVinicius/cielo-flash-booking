CREATE TABLE customer (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 200),
    email TEXT NOT NULL UNIQUE CHECK (email = lower(btrim(email))),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE event (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 200),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    available INTEGER NOT NULL CHECK (available BETWEEN 0 AND capacity),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservation (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES event (id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customer (id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'CANCELLED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    closure_reason_code TEXT,
    closure_reason_description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at),
    CHECK (
        (status = 'PENDING' AND closure_reason_code IS NULL AND closure_reason_description IS NULL)
        OR (status = 'CANCELLED'
            AND closure_reason_code IS NOT DISTINCT FROM 'CANCELLED_BY_REQUEST'
            AND closure_reason_description IS NOT DISTINCT FROM 'Reserva cancelada por solicitação')
        OR (status = 'EXPIRED'
            AND closure_reason_code IS NOT DISTINCT FROM 'RESERVATION_DEADLINE_REACHED'
            AND closure_reason_description IS NOT DISTINCT FROM 'Prazo da reserva encerrado')
    )
);

CREATE OR REPLACE FUNCTION prevent_terminal_closure_reason_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status IN ('CANCELLED', 'EXPIRED')
        AND (NEW.closure_reason_code IS DISTINCT FROM OLD.closure_reason_code
            OR NEW.closure_reason_description IS DISTINCT FROM OLD.closure_reason_description) THEN
        RAISE EXCEPTION 'terminal closure reason is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER reservation_terminal_closure_reason_immutable
BEFORE UPDATE ON reservation
FOR EACH ROW
EXECUTE FUNCTION prevent_terminal_closure_reason_change();

CREATE TABLE idempotency_record (
    id UUID PRIMARY KEY,
    idempotency_key TEXT NOT NULL UNIQUE CHECK (char_length(btrim(idempotency_key)) > 0),
    operation TEXT NOT NULL CHECK (operation IN ('POST', 'DELETE')),
    normalized_target TEXT NOT NULL CHECK (char_length(btrim(normalized_target)) > 0),
    payload_hash TEXT NOT NULL CHECK (char_length(btrim(payload_hash)) > 0),
    response_status INTEGER NOT NULL CHECK (response_status BETWEEN 100 AND 599),
    response_body JSONB NOT NULL CHECK (jsonb_typeof(response_body) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at)
);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type TEXT NOT NULL CHECK (char_length(btrim(aggregate_type)) > 0),
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL CHECK (event_type IN ('ReservationCreated', 'ReservationExpirationScheduled')),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0)
);

CREATE TABLE notification_delivery (
    id UUID PRIMARY KEY,
    outbox_event_id UUID NOT NULL UNIQUE REFERENCES outbox_event (id) ON DELETE RESTRICT,
    channel TEXT NOT NULL CHECK (channel = 'EMAIL'),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    provider_message_id TEXT,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX reservation_event_status_idx ON reservation (event_id, status);
CREATE INDEX reservation_customer_created_at_idx ON reservation (customer_id, created_at DESC);
CREATE INDEX reservation_pending_expires_at_idx ON reservation (expires_at) WHERE status = 'PENDING';
CREATE INDEX outbox_event_unpublished_occurred_at_idx ON outbox_event (occurred_at) WHERE published_at IS NULL;
CREATE INDEX idempotency_record_expires_at_idx ON idempotency_record (expires_at);
