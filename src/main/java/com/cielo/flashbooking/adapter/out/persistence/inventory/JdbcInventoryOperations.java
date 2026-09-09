package com.cielo.flashbooking.adapter.out.persistence.inventory;

import com.cielo.flashbooking.inventory.application.InventoryOperations;
import java.util.Objects;
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
    public boolean decrement(UUID eventId, int quantity) {
        validate(eventId, quantity);
        int affected = jdbcTemplate.update("""
                UPDATE event
                SET available = available - ?
                WHERE id = ? AND available >= ?
                """, quantity, eventId, quantity);
        return affected == 1;
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
