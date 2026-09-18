package com.cielo.flashbooking.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InitialSchemaIT extends LocalIntegrationInfrastructure {

    private Flyway flyway;

    @BeforeEach
    void migrateFromScratch() {
        flyway = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .cleanDisabled(false)
                .load();
        flyway.clean();
        assertThatCode(flyway::migrate).doesNotThrowAnyException();
    }

    @Test
    void createsEveryRequiredTableFromAnEmptyDatabase() throws SQLException {
        try (var connection = connection();
                var statement = connection.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            var resultSet = statement.executeQuery();
            var tableNames = new java.util.HashSet<String>();
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("table_name"));
            }

            assertThat(tableNames).contains("customer", "event", "reservation", "idempotency_record", "outbox_event", "notification_delivery");
        }
    }

    @Test
    void rejectsDuplicateCustomersAndReservationsWithoutRequiredRelations() throws SQLException {
        var customerId = UUID.randomUUID();
        insertCustomer(customerId, "customer@example.com");

        assertThatThrownBy(() -> insertCustomer(UUID.randomUUID(), "customer@example.com"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at)
                VALUES ('%s', '%s', '%s', 1, 'PENDING', now() + interval '10 minutes')
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), customerId)))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void rejectsInvalidInventoryReservationAndTerminalReasonValues() throws SQLException {
        assertThatThrownBy(() -> execute("""
                INSERT INTO event (id, name, capacity, available)
                VALUES ('%s', 'Invalid event', 0, 0)
                """.formatted(UUID.randomUUID())))
                .isInstanceOf(SQLException.class);

        var eventId = insertEvent();
        var customerId = UUID.randomUUID();
        insertCustomer(customerId, "valid@example.com");

        assertThatThrownBy(() -> execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at)
                VALUES ('%s', '%s', '%s', 0, 'PENDING', now() + interval '10 minutes')
                """.formatted(UUID.randomUUID(), eventId, customerId)))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at)
                VALUES ('%s', '%s', '%s', 1, 'CANCELLED', now() + interval '10 minutes')
                """.formatted(UUID.randomUUID(), eventId, customerId)))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void preservesImmutableCataloguedTerminalReasonsAndCommandIdempotency() throws SQLException {
        var eventId = insertEvent();
        var customerId = UUID.randomUUID();
        var reservationId = UUID.randomUUID();
        insertCustomer(customerId, "terminal@example.com");
        execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, closure_reason_code, closure_reason_description)
                VALUES ('%s', '%s', '%s', 1, 'CANCELLED', now() + interval '10 minutes',
                        'CANCELLED_BY_REQUEST', 'Reserva cancelada por solicitação')
                """.formatted(reservationId, eventId, customerId));

        assertThatThrownBy(() -> execute("""
                UPDATE reservation
                SET closure_reason_description = 'changed'
                WHERE id = '%s'
                """.formatted(reservationId)))
                .isInstanceOf(SQLException.class);

        var key = "command-key";
        insertIdempotencyRecord(key);
        assertThatThrownBy(() -> insertIdempotencyRecord(key)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertIdempotencyRecord("k".repeat(129))).isInstanceOf(SQLException.class);
        assertThatCode(() -> insertIdempotencyRecord("k".repeat(128))).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidEventSaleWindowsAndAcceptsAValidOne() {
        assertThatThrownBy(() -> execute("""
                INSERT INTO event (id, name, capacity, available, created_at, starts_at)
                VALUES ('%s', 'Invalid start', 1, 1, clock_timestamp(), clock_timestamp() - interval '1 second')
                """.formatted(UUID.randomUUID()))).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO event (id, name, capacity, available, created_at, ends_at)
                VALUES ('%s', 'Short end', 1, 1, clock_timestamp(), clock_timestamp() + interval '9 minutes')
                """.formatted(UUID.randomUUID()))).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO event (id, name, capacity, available, created_at, starts_at, ends_at)
                VALUES ('%s', 'Inverted', 1, 1, clock_timestamp(), clock_timestamp() + interval '2 hours', clock_timestamp() + interval '1 hour')
                """.formatted(UUID.randomUUID()))).isInstanceOf(SQLException.class);
        assertThatCode(() -> execute("""
                INSERT INTO event (id, name, capacity, available, created_at, starts_at, ends_at)
                VALUES ('%s', 'Valid', 1, 1, clock_timestamp(), clock_timestamp() + interval '1 hour', clock_timestamp() + interval '2 hours')
                """.formatted(UUID.randomUUID()))).doesNotThrowAnyException();
    }

    private Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    }

    private UUID insertEvent() throws SQLException {
        var eventId = UUID.randomUUID();
        execute("""
                INSERT INTO event (id, name, capacity, available)
                VALUES ('%s', 'Flash sale', 10, 10)
                """.formatted(eventId));
        return eventId;
    }

    private void insertCustomer(UUID customerId, String email) throws SQLException {
        execute("""
                INSERT INTO customer (id, name, email)
                VALUES ('%s', 'Customer', '%s')
                """.formatted(customerId, email));
    }

    private void insertIdempotencyRecord(String key) throws SQLException {
        execute("""
                INSERT INTO idempotency_record (id, idempotency_key, operation, normalized_target, payload_hash, response_status, response_body, expires_at)
                VALUES ('%s', '%s', 'POST', '/events', 'payload-hash', 201, '{}'::jsonb, now() + interval '24 hours')
                """.formatted(UUID.randomUUID(), key));
    }

    private void execute(String sql) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
