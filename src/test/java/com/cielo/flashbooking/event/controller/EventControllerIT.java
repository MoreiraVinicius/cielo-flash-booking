package com.cielo.flashbooking.event.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearEvents() {
        jdbcTemplate.update("DELETE FROM event");
    }

    @Test
    void createsAValidEventAndPersistsIt() throws Exception {
        String response = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", " Arena show ", "capacity", 120))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/events/[0-9a-f-]{36}")))
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Arena show"))
                .andExpect(jsonPath("$.capacity").value(120))
                .andExpect(jsonPath("$.available").value(120))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();
        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "SELECT name, capacity, available FROM event WHERE id = ?::uuid", id);
        assertThat(persisted)
                .containsEntry("name", "Arena show")
                .containsEntry("capacity", 120)
                .containsEntry("available", 120);
    }

    @ParameterizedTest
    @MethodSource("invalidEvents")
    void rejectsInvalidEventsWithoutPersistence(String name, int capacity) throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "capacity", capacity))))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.code").value("invalid-request"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class)).isZero();
    }

    private static Stream<Arguments> invalidEvents() {
        return Stream.of(
                Arguments.of("", 10),
                Arguments.of("x".repeat(201), 10),
                Arguments.of("valid", 0),
                Arguments.of("valid", -1));
    }
}
