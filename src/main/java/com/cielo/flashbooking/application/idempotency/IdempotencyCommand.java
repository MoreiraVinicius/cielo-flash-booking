package com.cielo.flashbooking.application.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record IdempotencyCommand(String key, String operation, String normalizedTarget, String payloadHash) {

    private static final int MAXIMUM_KEY_LENGTH = 128;

    public static IdempotencyCommand from(
            String key, String operation, String normalizedTarget, Object payload, ObjectMapper objectMapper) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        String normalizedKey = key.trim();
        if (normalizedKey.length() > MAXIMUM_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key must not exceed 128 characters");
        }
        try {
            return new IdempotencyCommand(
                    normalizedKey,
                    operation,
                    normalizedTarget,
                    sha256(objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize idempotency payload", exception);
        }
    }

    private static String sha256(String payload) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
