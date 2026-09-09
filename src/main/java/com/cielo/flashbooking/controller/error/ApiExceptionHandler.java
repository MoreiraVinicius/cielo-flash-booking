package com.cielo.flashbooking.controller.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public final class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleBadRequest(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "The request is invalid.",
                "invalid-request",
                request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                "The requested resource was not found.",
                "resource-not-found",
                request);
    }

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(ResourceConflictException exception, HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "Conflict",
                "The request conflicts with the current resource state.",
                "resource-conflict",
                request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ResponseEntity<ProblemDetail> handleServiceUnavailable(
            ServiceUnavailableException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service unavailable",
                "The service is temporarily unavailable.",
                "service-unavailable",
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected request failure correlationId={}", correlationId(request), exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred.",
                "internal-error",
                request);
    }

    private ResponseEntity<ProblemDetail> response(
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
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private String correlationId(HttpServletRequest request) {
        return (String) request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    }
}
