package com.cielo.flashbooking.application.idempotency;

public record IdempotencyResult(int status, String responseBody) {
}
