package com.cielo.flashbooking.event.summary;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("query-api")
class ExecutiveSummaryQueryIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OperationalSignalsReader operationalSignalsReader;

    @MockitoBean
    private ExecutiveNarrative executiveNarrative;

    @MockitoBean
    private DiscordSummaryPublisher discordSummaryPublisher;

    @BeforeEach
    void clearRows() {
        jdbcTemplate.update("DELETE FROM event_executive_summary");
        jdbcTemplate.update("DELETE FROM event");
        jdbcTemplate.update("UPDATE executive_summary_control SET enabled = FALSE, enabled_at = NULL WHERE id = TRUE");
    }

    @Test
    void returnsPersistedReportOrCurrentEligibilityWithoutExternalWork() throws Exception {
        Instant now = databaseNow();
        UUID disabledId = insertEvent(now.minusSeconds(600));
        mockMvc.perform(get("/events/{id}/executive-summary", disabledId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.deliveryStatus").value(org.hamcrest.Matchers.nullValue()));

        UUID eligibleId = insertEvent(now.plusSeconds(600));
        jdbcTemplate.update(
                "UPDATE executive_summary_control SET enabled = TRUE, enabled_at = ? WHERE id = TRUE",
                Timestamp.from(now));
        mockMvc.perform(get("/events/{id}/executive-summary", eligibleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.nullValue()));

        UUID olderId = insertEvent(now.minusSeconds(120));
        mockMvc.perform(get("/events/{id}/executive-summary", olderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_ELIGIBLE"));

        insertReport(eligibleId, now);
        mockMvc.perform(get("/events/{id}/executive-summary", eligibleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.markdown").value("# Saved report"))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.asOf").isNotEmpty())
                .andExpect(jsonPath("$.deliveryStatus").value("SENT"));

        jdbcTemplate.update("UPDATE event SET name = 'Mutated source' WHERE id = ?", eligibleId);
        mockMvc.perform(get("/events/{id}/executive-summary", eligibleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value("# Saved report"));

        verify(operationalSignalsReader, never())
                .read(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(executiveNarrative, never()).write(org.mockito.ArgumentMatchers.any());
        verify(discordSummaryPublisher, never())
                .publish(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void returns404WhenEventDoesNotExist() throws Exception {
        mockMvc.perform(get("/events/{id}/executive-summary", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID insertEvent(Instant startsAt) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at, starts_at) VALUES (?, 'Query event', 10, 10, ?, ?)",
                eventId,
                Timestamp.from(startsAt.minusSeconds(60)),
                Timestamp.from(startsAt));
        return eventId;
    }

    private void insertReport(UUID eventId, Instant timestamp) {
        jdbcTemplate.update("""
                INSERT INTO event_executive_summary (event_id, status, delivery_status, as_of, generated_at, markdown)
                VALUES (?, 'READY', 'SENT', ?, ?, '# Saved report')
                """, eventId, Timestamp.from(timestamp.minusSeconds(1)), Timestamp.from(timestamp));
    }

    private Instant databaseNow() {
        return jdbcTemplate
                .queryForObject("SELECT clock_timestamp()", Timestamp.class)
                .toInstant();
    }
}
