package com.cielo.flashbooking.application.idempotency;

public record IdempotencyResponse(int status, Object body) {
}
