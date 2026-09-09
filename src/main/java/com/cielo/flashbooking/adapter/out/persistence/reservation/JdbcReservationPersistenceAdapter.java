package com.cielo.flashbooking.adapter.out.persistence.reservation;

import com.cielo.flashbooking.domain.reservation.Customer;
import com.cielo.flashbooking.domain.reservation.Reservation;
import com.cielo.flashbooking.reservation.application.ReservationWriter;
import com.cielo.flashbooking.reservation.application.ReservationReader;
import com.cielo.flashbooking.reservation.application.ReservationDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReservationPersistenceAdapter implements ReservationWriter, ReservationReader {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcReservationPersistenceAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean eventExists(UUID eventId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM event WHERE id = ?)", Boolean.class, eventId));
    }

    @Override
    public Optional<ReservationDetails> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT r.id AS reservation_id, r.quantity, r.status, r.expires_at,
                       r.closure_reason_code, r.closure_reason_description,
                       e.id AS event_id, e.name AS event_name, e.capacity AS event_capacity, e.available AS event_available,
                       c.id AS customer_id, c.name AS customer_name, c.email AS customer_email
                FROM reservation r
                JOIN event e ON e.id = r.event_id
                JOIN customer c ON c.id = r.customer_id
                WHERE r.id = ?
                """, resultSet -> resultSet.next() ? Optional.of(toDetails(resultSet)) : Optional.empty(), id);
    }

    @Override
    public Customer upsertCustomer(Customer customer) {
        UUID id = jdbcTemplate.queryForObject("""
                INSERT INTO customer (id, name, email, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (email) DO UPDATE
                SET name = EXCLUDED.name, updated_at = EXCLUDED.updated_at
                RETURNING id
                """, UUID.class,
                customer.id(),
                customer.name(),
                customer.email(),
                Timestamp.from(customer.createdAt()),
                Timestamp.from(customer.updatedAt()));
        return Customer.create(id, customer.name(), customer.email(), customer.createdAt());
    }

    @Override
    public void save(Reservation reservation) {
        jdbcTemplate.update("""
                INSERT INTO reservation (
                    id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                reservation.id(),
                reservation.eventId(),
                reservation.customerId(),
                reservation.quantity(),
                reservation.status().name(),
                Timestamp.from(reservation.expiresAt()),
                Timestamp.from(reservation.createdAt()),
                Timestamp.from(reservation.updatedAt()));
    }

    @Override
    public void addReservationCreatedOutboxEvent(Reservation reservation) {
        jdbcTemplate.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
                VALUES (?, ?, ?, 'ReservationCreated', ?::jsonb, ?)
                """,
                UUID.randomUUID(),
                "Reservation",
                reservation.id(),
                reservationPayload(reservation),
                Timestamp.from(reservation.createdAt()));
    }

    private String reservationPayload(Reservation reservation) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "reservationId", reservation.id().toString(),
                    "eventId", reservation.eventId().toString(),
                    "customerId", reservation.customerId().toString(),
                    "quantity", reservation.quantity(),
                    "expiresAt", reservation.expiresAt().toString()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize reservation outbox payload", exception);
        }
    }

    private ReservationDetails toDetails(ResultSet resultSet) throws SQLException {
        String closureReasonCode = resultSet.getString("closure_reason_code");
        ReservationDetails.ClosureReason closureReason = closureReasonCode == null
                ? null
                : new ReservationDetails.ClosureReason(
                        closureReasonCode,
                        resultSet.getString("closure_reason_description"));
        return new ReservationDetails(
                resultSet.getObject("reservation_id", UUID.class),
                new ReservationDetails.Event(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getString("event_name"),
                        resultSet.getInt("event_capacity"),
                        resultSet.getInt("event_available")),
                new ReservationDetails.Customer(
                        resultSet.getObject("customer_id", UUID.class),
                        resultSet.getString("customer_name"),
                        resultSet.getString("customer_email")),
                resultSet.getInt("quantity"),
                com.cielo.flashbooking.domain.reservation.ReservationStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("expires_at").toInstant(),
                closureReason);
    }
}
