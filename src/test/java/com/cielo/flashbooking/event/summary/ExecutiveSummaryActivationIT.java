package com.cielo.flashbooking.event.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("command-api")
class ExecutiveSummaryActivationIT extends LocalIntegrationInfrastructure {

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

    @Autowired
    private JsonMapper objectMapper;

    @BeforeEach
    void disableGlobalActivation() {
        jdbcTemplate.update(
                "UPDATE executive_summary_control SET enabled = FALSE, enabled_at = NULL, updated_at = clock_timestamp() WHERE id = TRUE");
    }

    @Test
    void enablesGloballyAndRepeatedEnableDoesNotRestartTheWindow() throws Exception {
        mockMvc.perform(put("/executive-summary/activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.enabledAt").isNotEmpty());

        String firstEnabledAt = jdbcTemplate.queryForObject(
                "SELECT enabled_at::text FROM executive_summary_control WHERE id = TRUE", String.class);

        mockMvc.perform(put("/executive-summary/activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT enabled_at::text FROM executive_summary_control WHERE id = TRUE", String.class))
                .isEqualTo(firstEnabledAt);
    }

    @Test
    void disablingClearsTheActiveWindowAndReturnsPersistedState() throws Exception {
        putActivation(true);

        mockMvc.perform(put("/executive-summary/activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.enabledAt").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT enabled FROM executive_summary_control WHERE id = TRUE", Boolean.class))
                .isFalse();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT enabled_at FROM executive_summary_control WHERE id = TRUE", java.sql.Timestamp.class))
                .isNull();
    }

    @Test
    void rejectsMissingNullAndNonBooleanValuesWithoutChangingGlobalState() throws Exception {
        putActivation(true);
        String enabledAt = jdbcTemplate.queryForObject(
                "SELECT enabled_at::text FROM executive_summary_control WHERE id = TRUE", String.class);

        for (String body : new String[] {"{}", "{\"enabled\":null}", "{\"enabled\":\"yes\"}"}) {
            mockMvc.perform(put("/executive-summary/activation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT enabled_at::text FROM executive_summary_control WHERE id = TRUE", String.class))
                .isEqualTo(enabledAt);
    }

    @Test
    void concurrentEnableRequestsKeepOneWindowStart() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var requests = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(ignored -> executor.submit(() -> {
                        try {
                            putActivation(true);
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    }))
                    .toList();
            for (var request : requests) {
                request.get();
            }
        }

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT enabled FROM executive_summary_control WHERE id = TRUE", Boolean.class))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM executive_summary_control WHERE id = TRUE AND enabled_at IS NOT NULL",
                        Integer.class))
                .isEqualTo(1);
    }

    private void putActivation(boolean enabled) throws Exception {
        mockMvc.perform(put("/executive-summary/activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", enabled))))
                .andExpect(status().isOk());
    }
}
