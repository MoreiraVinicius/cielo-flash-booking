package com.cielo.flashbooking.event.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ExecutiveSummaryFactsIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private EventSummaryFactsReader factsReader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reconstructsVolumeAndReservationValidityAtEventClose() {
        Instant startsAt = Instant.parse("2026-10-01T10:00:00Z");
        Instant endsAt = startsAt.plusSeconds(1800);
        UUID eventId = insertEvent("Close facts", 20, startsAt, endsAt);
        insertReservation(eventId, startsAt, startsAt.plusSeconds(3600), 2, "CANCELLED", startsAt.plusSeconds(600));
        insertReservation(
                eventId, startsAt.plusSeconds(60), startsAt.plusSeconds(900), 3, "EXPIRED", endsAt.plusSeconds(60));
        insertReservation(
                eventId, startsAt.plusSeconds(300), startsAt.plusSeconds(3600), 4, "CANCELLED", endsAt.plusSeconds(60));
        insertReservation(
                eventId, startsAt.plusSeconds(310), endsAt.plusSeconds(600), 6, "PENDING", startsAt.plusSeconds(310));

        EventSummaryFacts facts = factsReader.read(eventId).orElseThrow();

        assertThat(facts.complete()).isTrue();
        assertThat(facts.acceptedReservations()).isEqualTo(4);
        assertThat(facts.acceptedTickets()).isEqualTo(15);
        assertThat(facts.validTicketsAtClose()).isEqualTo(10);
        assertThat(facts.cancelledTicketsAtClose()).isEqualTo(2);
        assertThat(facts.expiredTicketsAtClose()).isEqualTo(3);
        assertThat(facts.peakMinute()).isEqualTo(startsAt.plusSeconds(300));
        assertThat(facts.peakTickets()).isEqualTo(10);
        assertThat(facts.firstFiveMinuteTickets()).isEqualTo(5);
        assertThat(facts.saleStartsAt()).isEqualTo(startsAt);
        assertThat(facts.endsAt()).isEqualTo(endsAt);
    }

    @Test
    void isolatesEventsAndOmitsRhythmForShortOrEmptyEvents() {
        Instant startsAt = Instant.parse("2026-10-01T10:00:00Z");
        UUID firstEvent = insertEvent("First", 20, startsAt, startsAt.plusSeconds(300));
        UUID secondEvent = insertEvent("Second", 20, startsAt, startsAt.plusSeconds(599));
        insertReservation(
                secondEvent,
                startsAt.plusSeconds(30),
                startsAt.plusSeconds(900),
                7,
                "PENDING",
                startsAt.plusSeconds(30));

        EventSummaryFacts empty = factsReader.read(firstEvent).orElseThrow();
        EventSummaryFacts shortEvent = factsReader.read(secondEvent).orElseThrow();

        assertThat(empty.acceptedReservations()).isZero();
        assertThat(empty.acceptedTickets()).isZero();
        assertThat(empty.peakMinute()).isNull();
        assertThat(empty.peakTickets()).isNull();
        assertThat(empty.firstFiveMinuteTickets()).isNull();
        assertThat(empty.complete()).isTrue();
        assertThat(shortEvent.acceptedReservations()).isEqualTo(1);
        assertThat(shortEvent.acceptedTickets()).isEqualTo(7);
        assertThat(shortEvent.validTicketsAtClose()).isEqualTo(7);
        assertThat(shortEvent.firstFiveMinuteTickets()).isNull();
        assertThat(shortEvent.peakTickets()).isEqualTo(7);
    }

    @Test
    void flagsInconsistentClosePartitionInsteadOfCallingItComplete() {
        Instant startsAt = Instant.parse("2026-10-01T10:00:00Z");
        UUID eventId = insertEvent("Inconsistent", 20, startsAt, startsAt.plusSeconds(1800));
        insertReservation(
                eventId, startsAt.plusSeconds(10), startsAt.plusSeconds(3600), 5, "EXPIRED", startsAt.plusSeconds(20));

        EventSummaryFacts facts = factsReader.read(eventId).orElseThrow();

        assertThat(facts.acceptedTickets()).isEqualTo(5);
        assertThat(facts.validTicketsAtClose() + facts.cancelledTicketsAtClose() + facts.expiredTicketsAtClose())
                .isZero();
        assertThat(facts.complete()).isFalse();
    }

    @Test
    void ignoresEventsWithoutACommercialEndAndUnknownEvents() {
        UUID openEvent = insertEvent("Open", 10, null, null);

        assertThat(factsReader.read(openEvent)).isEmpty();
        assertThat(factsReader.read(UUID.randomUUID())).isEmpty();
    }

    private UUID insertEvent(String name, int capacity, Instant startsAt, Instant endsAt) {
        UUID eventId = UUID.randomUUID();
        Instant createdAt = startsAt == null ? Instant.now().minusSeconds(60) : startsAt.minusSeconds(60);
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at, starts_at, ends_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                eventId,
                name,
                capacity,
                capacity,
                Timestamp.from(createdAt),
                startsAt == null ? null : Timestamp.from(startsAt),
                endsAt == null ? null : Timestamp.from(endsAt));
        return eventId;
    }

    private void insertReservation(
            UUID eventId, Instant createdAt, Instant expiresAt, int quantity, String status, Instant updatedAt) {
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email) VALUES (?, 'Test Buyer', ?)",
                customerId,
                customerId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at, closure_reason_code, closure_reason_description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                eventId,
                customerId,
                quantity,
                status,
                Timestamp.from(expiresAt),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt),
                "CANCELLED".equals(status)
                        ? "CANCELLED_BY_REQUEST"
                        : "EXPIRED".equals(status) ? "RESERVATION_DEADLINE_REACHED" : null,
                "CANCELLED".equals(status)
                        ? "Reserva cancelada por solicitação"
                        : "EXPIRED".equals(status) ? "Prazo da reserva encerrado" : null);
    }
}
