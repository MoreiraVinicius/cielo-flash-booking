package com.cielo.flashbooking.event.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("command-api")
class ExecutiveSummaryDeliveryIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExecutiveSummaryReportStore reportStore;

    @BeforeEach
    void clearRows() {
        jdbcTemplate.update("DELETE FROM event_executive_summary");
        jdbcTemplate.update("DELETE FROM event");
    }

    @Test
    void deliveryStateBecomesUnknownBeforeOnlyOneAttemptAndSentGetsDatabaseConfirmationTime() {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available) VALUES (?, 'Delivery test', 10, 10)", eventId);
        jdbcTemplate.update("""
                INSERT INTO event_executive_summary (event_id, status, delivery_status, as_of, generated_at, markdown)
                VALUES (?, 'PARTIAL', 'NOT_CONFIGURED', ?, ?, '# Persisted markdown')
                """, eventId, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));

        assertThat(reportStore.beginDelivery(eventId)).contains("# Persisted markdown");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT delivery_status FROM event_executive_summary WHERE event_id = ?",
                        String.class,
                        eventId))
                .isEqualTo("UNKNOWN");
        assertThat(reportStore.beginDelivery(eventId)).isEmpty();

        reportStore.finishDelivery(eventId, DiscordSummaryPublisher.DeliveryStatus.SENT);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT delivery_status FROM event_executive_summary WHERE event_id = ?",
                        String.class,
                        eventId))
                .isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT delivery_confirmed_at FROM event_executive_summary WHERE event_id = ?",
                        Timestamp.class,
                        eventId))
                .isNotNull();
    }
}
