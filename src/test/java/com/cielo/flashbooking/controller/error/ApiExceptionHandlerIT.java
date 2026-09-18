package com.cielo.flashbooking.controller.error;

import com.cielo.flashbooking.application.error.ResourceConflictException;
import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import static org.assertj.core.api.Assertions.assertThat;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cielo.flashbooking.config.security.RequestPayloadLimitFilter;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
@Import({ApiExceptionHandler.class, ProblemResponseFactory.class, CorrelationIdFilter.class, RequestPayloadLimitFilter.class, ErrorTestController.class})
class ApiExceptionHandlerIT {

    @Autowired
    private MockMvc mockMvc;

    private final Logger apiExceptionLogger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    @BeforeEach
    void attachLogCapture() {
        logAppender.start();
        apiExceptionLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogCapture() {
        apiExceptionLogger.detachAppender(logAppender);
        logAppender.stop();
    }

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
    void unexpectedError_logsThrowableWithCorrelationIdAndHidesItsDetails() throws Exception {
        mockMvc.perform(get("/__test/errors/unexpected")
                        .header(CorrelationIdFilter.HEADER_NAME, "unexpected-request"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "unexpected-request"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.code").value("internal-error"))
                .andExpect(jsonPath("$.correlationId").value("unexpected-request"))
                .andExpect(content().string(not(containsString("database-password"))));

        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("correlationId=unexpected-request");
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("database-password");
        });
    }

    @Test
    void rejectsOversizedRequestsBeforeTheyReachTheController() throws Exception {
        mockMvc.perform(post("/__test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("x".repeat(65537)))
                .andExpect(status().isPayloadTooLarge());
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
