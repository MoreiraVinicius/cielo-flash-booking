package com.cielo.flashbooking.controller.error;

import com.cielo.flashbooking.application.error.ResourceConflictException;
import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemResponseFactory {

    private final ObjectMapper objectMapper;

    public ProblemResponseFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProblemDetail expectedFailure(RuntimeException exception, HttpServletRequest request) {
        if (exception instanceof ResourceNotFoundException) {
            return problem(HttpStatus.NOT_FOUND, "Resource not found", "The requested resource was not found.", "resource-not-found", request);
        }
        if (exception instanceof ResourceConflictException) {
            return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with the current resource state.", "resource-conflict", request);
        }
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request is invalid.", "invalid-request", request);
    }

    public ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            String code,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:flash-booking:problem:" + code));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId(request));
        return problem;
    }

    public String correlationId(HttpServletRequest request) {
        return (String) request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    }

    public String withCurrentCorrelationId(String responseBody, HttpServletRequest request) {
        try {
            ObjectNode problem = (ObjectNode) objectMapper.readTree(responseBody);
            problem.put("correlationId", correlationId(request));
            return objectMapper.writeValueAsString(problem);
        } catch (JsonProcessingException | ClassCastException exception) {
            throw new IllegalStateException("could not refresh problem correlation id", exception);
        }
    }
}
