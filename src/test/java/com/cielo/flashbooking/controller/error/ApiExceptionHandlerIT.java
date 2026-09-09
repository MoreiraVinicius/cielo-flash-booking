package com.cielo.flashbooking.controller.error;

import com.cielo.flashbooking.application.error.ResourceConflictException;
import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ErrorTestController.class)
@Import({ApiExceptionHandler.class, ProblemResponseFactory.class, CorrelationIdFilter.class, ErrorTestController.class})
class ApiExceptionHandlerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsStableBadRequestProblem() throws Exception {
        mockMvc.perform(post("/__test/errors/validation")
                        .header(CorrelationIdFilter.HEADER_NAME, "request-400")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "request-400"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("The request is invalid."))
                .andExpect(jsonPath("$.code").value("invalid-request"))
                .andExpect(jsonPath("$.correlationId").value("request-400"));
    }

    @Test
    void returnsStableNotFoundProblem() throws Exception {
        assertProblem("not-found", 404, "Resource not found", "resource-not-found");
    }

    @Test
    void returnsStableConflictProblem() throws Exception {
        assertProblem("conflict", 409, "Conflict", "resource-conflict");
    }

    @Test
    void hidesUnexpectedErrorDetailsAndGeneratesCorrelationId() throws Exception {
        mockMvc.perform(get("/__test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(
                        CorrelationIdFilter.HEADER_NAME,
                        matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.code").value("internal-error"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
                .andExpect(content().string(not(containsString("database-password"))));
    }

    private void assertProblem(String path, int expectedStatus, String expectedTitle, String expectedCode)
            throws Exception {
        mockMvc.perform(get("/__test/errors/{path}", path)
                        .header(CorrelationIdFilter.HEADER_NAME, "client-correlation"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "client-correlation"))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.correlationId").value("client-correlation"));
    }

}

@RestController
@RequestMapping("/__test/errors")
class ErrorTestController {

    @PostMapping("/validation")
    void validation(@Valid @RequestBody ErrorTestRequest request) {
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new ResourceNotFoundException("secret resource lookup detail");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ResourceConflictException("secret conflict detail");
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException("database-password");
    }
}

record ErrorTestRequest(@NotBlank String value) {
}
