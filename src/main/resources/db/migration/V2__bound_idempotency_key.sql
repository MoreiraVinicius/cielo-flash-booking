ALTER TABLE idempotency_record
    ADD CONSTRAINT idempotency_record_key_length_check
    CHECK (char_length(idempotency_key) BETWEEN 1 AND 128);
