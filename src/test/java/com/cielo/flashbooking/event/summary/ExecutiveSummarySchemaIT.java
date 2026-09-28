package com.cielo.flashbooking.event.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ExecutiveSummarySchemaIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSummaryData() {
        jdbcTemplate.update("DELETE FROM event_executive_summary");
        jdbcTemplate.update("DELETE FROM event");
        jdbcTemplate.update("UPDATE executive_summary_control SET enabled = FALSE, enabled_at = NULL WHERE id = TRUE");
    }

    @Test
    void migration_whenApplied_createsOneGlobalControlDisabledByDefault() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT enabled FROM executive_summary_control WHERE id = TRUE", Boolean.class))
                .isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM executive_summary_control", Long.class))
                .isEqualTo(1L);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO executive_summary_control (id) VALUES (TRUE)"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void report_whenStored_rejectsDuplicateEventAndInvalidDeliveryStatus() {
        UUID eventId = insertEvent();
        insertReport(eventId, "UNKNOWN", 0);

        assertThatThrownBy(() -> insertReport(eventId, "SENT", 0)).isInstanceOf(DataAccessException.class);
        UUID secondEventId = insertEvent();
        assertThatThrownBy(() -> insertReport(secondEventId, "SENDING", 0)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void report_whenFactsAreNegative_rejectsTheRow() {
        UUID eventId = insertEvent();

        assertThatThrownBy(() -> insertReport(eventId, "NOT_CONFIGURED", -1)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void schema_usesTimezoneAwareInstantsAndBigintCounts() {
        assertThat(columnType("event", "first_available_zero_at")).isEqualTo("timestamp with time zone");
        assertThat(columnType("event_executive_summary", "as_of")).isEqualTo("timestamp with time zone");
        assertThat(columnType("event_executive_summary", "accepted_tickets")).isEqualTo("bigint");
    }

    @Test
    void schema_doesNotPersistPersonalDataWebhookUrlOrRawPrompt() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'event_executive_summary'
                """, String.class);

        assertThat(columns).doesNotContain("customer_id", "customer_name", "customer_email", "webhook_url", "prompt");
    }

    private UUID insertEvent() {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available) VALUES (?, 'Summary schema event', 10, 10)",
                eventId);
        return eventId;
    }

    private void insertReport(UUID eventId, String deliveryStatus, long acceptedTickets) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO event_executive_summary (
                    event_id, status, delivery_status, as_of, generated_at, markdown, accepted_tickets)
                VALUES (?, 'PARTIAL', ?, ?, ?, 'Report', ?)
                """,
                eventId,
                deliveryStatus,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                acceptedTickets);
    }

    private String columnType(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }
}
