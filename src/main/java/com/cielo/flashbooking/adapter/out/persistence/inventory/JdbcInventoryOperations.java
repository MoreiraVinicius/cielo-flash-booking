package com.cielo.flashbooking.adapter.out.persistence.inventory;

import com.cielo.flashbooking.inventory.application.InventoryOperations;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInventoryOperations implements InventoryOperations {

    private final JdbcTemplate jdbcTemplate;

    JdbcInventoryOperations(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Instant> decrement(UUID eventId, int quantity) {
        validate(eventId, quantity);
        return jdbcTemplate.query(
                """
                UPDATE event
                SET available = available - ?,
                    first_available_zero_at = CASE
                        WHEN available = ?
                            AND first_available_zero_at IS NULL
                            AND (SELECT enabled FROM executive_summary_control WHERE id = TRUE)
                        THEN clock_timestamp()
                        ELSE first_available_zero_at
                    END
                WHERE id = ?
                  AND available >= ?
                  AND (starts_at IS NULL OR starts_at <= clock_timestamp())
                  AND (ends_at IS NULL OR clock_timestamp() < ends_at)
                RETURNING clock_timestamp()
                """,
                resultSet ->
                        resultSet.next() ? Optional.of(resultSet.getTimestamp(1).toInstant()) : Optional.empty(),
                quantity,
                quantity,
                eventId,
                quantity);
    }

    @Override
    public boolean increment(UUID eventId, int quantity) {
        validate(eventId, quantity);
        int affected = jdbcTemplate.update("""
                UPDATE event
                SET available = available + ?
                WHERE id = ? AND available + ? <= capacity
                """, quantity, eventId, quantity);
        return affected == 1;
    }

    private void validate(UUID eventId, int quantity) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
