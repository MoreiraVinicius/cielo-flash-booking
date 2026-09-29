ALTER TABLE outbox_event
    DROP CONSTRAINT outbox_event_event_type_check;

ALTER TABLE outbox_event
    ADD CONSTRAINT outbox_event_event_type_check CHECK (
        event_type IN (
            'ReservationCreated',
            'ReservationExpirationScheduled',
            'ReservationHeld',
            'ReservationConfirmed',
            'ReservationConfirmationRejected',
            'ReservationHoldClosed',
            'ReservationCancellationRequested'
        )
    );
