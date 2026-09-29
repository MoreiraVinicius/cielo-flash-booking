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
                var statement = connection.prepareStatement(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            var resultSet = statement.executeQuery();
            var tableNames = new java.util.HashSet<String>();
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("table_name"));
            }

            assertThat(tableNames)
                    .contains(
                            "customer",
                            "event",
                            "reservation",
                            "confirmation_inbox",
                            "idempotency_record",
                            "outbox_event",
                            "notification_delivery");
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
                """.formatted(UUID.randomUUID()))).isInstanceOf(SQLException.class);

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
    void enforcesCoherentConfirmationAndCancellationLifecycleMetadata() throws SQLException {
        var eventId = insertEvent();
        var customerId = UUID.randomUUID();
        insertCustomer(customerId, "lifecycle@example.com");

        var confirmedId = UUID.randomUUID();
        execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, confirmed_at)
                VALUES ('%s', '%s', '%s', 1, 'CONFIRMED', now() + interval '10 minutes', clock_timestamp())
                """.formatted(confirmedId, eventId, customerId));

        var cancellationId = UUID.randomUUID();
        var cancellationPendingId = UUID.randomUUID();
        execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, confirmed_at, cancellation_id)
                VALUES ('%s', '%s', '%s', 1, 'CANCELLATION_PENDING', now() + interval '10 minutes', clock_timestamp(), '%s')
                """.formatted(cancellationPendingId, eventId, customerId, cancellationId));

        var cancelledAfterConfirmationId = UUID.randomUUID();
        execute("""
                INSERT INTO reservation (
                    id, event_id, customer_id, quantity, status, expires_at, confirmed_at, cancellation_id,
                    closure_reason_code, closure_reason_description
                )
                VALUES (
                    '%s', '%s', '%s', 1, 'CANCELLED', now() + interval '10 minutes', clock_timestamp(), '%s',
                    'CANCELLED_BY_REQUEST', 'Reserva cancelada por solicitação'
                )
                """.formatted(cancelledAfterConfirmationId, eventId, customerId, UUID.randomUUID()));

        assertThatThrownBy(() -> execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at)
                VALUES ('%s', '%s', '%s', 1, 'CONFIRMED', now() + interval '10 minutes')
                """.formatted(UUID.randomUUID(), eventId, customerId)))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, confirmed_at)
                VALUES ('%s', '%s', '%s', 1, 'PENDING', now() + interval '10 minutes', clock_timestamp())
                """.formatted(UUID.randomUUID(), eventId, customerId)))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, confirmed_at)
                VALUES ('%s', '%s', '%s', 1, 'CANCELLATION_PENDING', now() + interval '10 minutes', clock_timestamp())
                """.formatted(UUID.randomUUID(), eventId, customerId)))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, cancellation_id)
                VALUES ('%s', '%s', '%s', 1, 'CANCELLATION_PENDING', now() + interval '10 minutes', '%s')
                """.formatted(UUID.randomUUID(), eventId, customerId, UUID.randomUUID())))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, confirmed_at)
                VALUES ('%s', '%s', '%s', 1, 'CANCELLED', now() + interval '10 minutes', clock_timestamp())
                """.formatted(UUID.randomUUID(), eventId, customerId)))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, confirmed_at, cancellation_id)
                VALUES ('%s', '%s', '%s', 1, 'EXPIRED', now() + interval '10 minutes', clock_timestamp(), '%s')
                """.formatted(UUID.randomUUID(), eventId, customerId, UUID.randomUUID())))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, confirmed_at, cancellation_id)
                VALUES ('%s', '%s', '%s', 1, 'CANCELLATION_PENDING', now() + interval '10 minutes', clock_timestamp(), '%s')
                """.formatted(UUID.randomUUID(), eventId, customerId, cancellationId)))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void storesInboxResolutionsWithStableBusinessIdentityAndAllowsUnknownReservation() throws SQLException {
        var source = "reservation-owner";
        var resolutionId = "resolution-" + UUID.randomUUID();
        var fingerprint = "a".repeat(64);
        execute("""
                INSERT INTO confirmation_inbox (
                    source, resolution_id, reservation_id, message_type, payload_fingerprint, outcome
                ) VALUES ('%s', '%s', NULL, 'ReservationConfirmationRequested', '%s', '{"status":"NOT_FOUND"}'::jsonb)
                """.formatted(source, resolutionId, fingerprint));

        assertThatThrownBy(() -> execute("""
                INSERT INTO confirmation_inbox (
                    source, resolution_id, reservation_id, message_type, payload_fingerprint, outcome
                ) VALUES ('%s', '%s', NULL, 'ReservationConfirmationRequested', '%s', '{"status":"NOT_FOUND"}'::jsonb)
                """.formatted(source, resolutionId, "b".repeat(64))))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> execute("""
                INSERT INTO confirmation_inbox (
                    source, resolution_id, message_type, payload_fingerprint, outcome
                ) VALUES ('%s', '%s', 'ReservationCancellationCompleted', '%s', '{"status":"COMPLETED"}'::jsonb)
                """.formatted(source, "cancellation-" + UUID.randomUUID(), fingerprint)))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> execute("""
                INSERT INTO confirmation_inbox (
                    source, resolution_id, message_type, payload_fingerprint, outcome
                ) VALUES ('%s', '%s', 'UnknownMessage', '%s', '{}'::jsonb)
                """.formatted(source, "unknown-" + UUID.randomUUID(), fingerprint)))
                .isInstanceOf(SQLException.class);

        var eventId = insertEvent();
        var customerId = UUID.randomUUID();
        var reservationId = UUID.randomUUID();
        insertCustomer(customerId, "inbox-retention@example.com");
        execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at)
                VALUES ('%s', '%s', '%s', 1, 'PENDING', now() + interval '10 minutes')
                """.formatted(reservationId, eventId, customerId));
        execute("""
                INSERT INTO confirmation_inbox (
                    source, resolution_id, reservation_id, message_type, payload_fingerprint, outcome
                ) VALUES ('%s', '%s', '%s', 'ReservationConfirmationRequested', '%s', '{"status":"PENDING"}'::jsonb)
                """.formatted(source, "reservation-retention-" + UUID.randomUUID(), reservationId, fingerprint));

        assertThatThrownBy(() -> execute("DELETE FROM reservation WHERE id = '%s'".formatted(reservationId)))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void migratesExistingVersionFiveReservationRowsWithoutChangingTheirLifecycle() throws SQLException {
        flyway.clean();
        var versionFive = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target("5")
                .load();
        assertThatCode(versionFive::migrate).doesNotThrowAnyException();

        var eventId = insertEvent();
        var customerId = UUID.randomUUID();
        insertCustomer(customerId, "legacy@example.com");
        var pendingId = UUID.randomUUID();
        var cancelledId = UUID.randomUUID();
        var expiredId = UUID.randomUUID();
        execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at)
                VALUES ('%s', '%s', '%s', 1, 'PENDING', now() + interval '10 minutes')
                """.formatted(pendingId, eventId, customerId));
        execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, closure_reason_code, closure_reason_description)
                VALUES ('%s', '%s', '%s', 1, 'CANCELLED', now() + interval '10 minutes', 'CANCELLED_BY_REQUEST', 'Reserva cancelada por solicitação')
                """.formatted(cancelledId, eventId, customerId));
        execute("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, closure_reason_code, closure_reason_description)
                VALUES ('%s', '%s', '%s', 1, 'EXPIRED', now() + interval '10 minutes', 'RESERVATION_DEADLINE_REACHED', 'Prazo da reserva encerrado')
                """.formatted(expiredId, eventId, customerId));

        var latest = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .load();
        assertThatCode(latest::migrate).doesNotThrowAnyException();
        assertThat(queryStatus(pendingId)).isEqualTo("PENDING");
        assertThat(queryStatus(cancelledId)).isEqualTo("CANCELLED");
        assertThat(queryStatus(expiredId)).isEqualTo("EXPIRED");
        assertThat(hasConfirmedAt(pendingId)).isFalse();
        assertThat(hasConfirmedAt(cancelledId)).isFalse();
        assertThat(hasConfirmedAt(expiredId)).isFalse();
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
                """.formatted(reservationId))).isInstanceOf(SQLException.class);

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
        return java.sql.DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
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

    private String queryStatus(UUID reservationId) {
        try (var connection = connection();
                var statement = connection.prepareStatement("SELECT status FROM reservation WHERE id = ?")) {
            statement.setObject(1, reservationId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Expected migrated reservation to exist");
                }
                return resultSet.getString(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not query migrated reservation status", exception);
        }
    }

    private boolean hasConfirmedAt(UUID reservationId) {
        try (var connection = connection();
                var statement =
                        connection.prepareStatement("SELECT confirmed_at IS NOT NULL FROM reservation WHERE id = ?")) {
            statement.setObject(1, reservationId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Expected migrated reservation to exist");
                }
                return resultSet.getBoolean(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not query migrated reservation confirmation state", exception);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var connection = connection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
