package com.cielo.flashbooking.adapter.out.persistence.event;

import com.cielo.flashbooking.domain.event.Event;
import com.cielo.flashbooking.event.application.EventReader;
import com.cielo.flashbooking.event.application.EventWriter;
import java.util.Optional;
import java.util.UUID;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class EventPersistenceAdapter implements EventWriter, EventReader {

    private final JdbcTemplate jdbcTemplate;

    EventPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Event save(Event event) {
        jdbcTemplate.update("""
                INSERT INTO event (id, name, capacity, available, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                event.id(),
                event.name(),
                event.capacity(),
                event.available(),
                Timestamp.from(event.createdAt()));
        return event;
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT id, name, capacity, available, created_at
                FROM event
                WHERE id = ?
                """, resultSet -> resultSet.next()
                ? Optional.of(Event.restore(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getInt("capacity"),
                        resultSet.getInt("available"),
                        resultSet.getTimestamp("created_at").toInstant()))
                : Optional.empty(), id);
    }
}
