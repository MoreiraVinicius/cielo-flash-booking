package com.cielo.flashbooking.reservation.confirm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public record ReservationResolutionMessage(
        Type type, String source, String resolutionId, UUID reservationId, Instant requestedAt, UUID cancellationId) {

    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final Set<String> COMMON_FIELDS =
            Set.of("version", "type", "source", "resolutionId", "reservationId");

    public static ReservationResolutionMessage parse(String body, JsonMapper objectMapper) {
        if (!StringUtils.hasText(body) || body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("reservation resolution body is empty or too large");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("reservation resolution body is not valid JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("reservation resolution body must be a JSON object");
        }

        JsonNode version = root.get("version");
        if (version == null || !version.isIntegralNumber() || version.asInt() != 1) {
            throw new IllegalArgumentException("reservation resolution version is not supported");
        }

        Type type = Type.fromWireValue(requiredText(root, "type"));
        Set<String> expectedFields = new HashSet<>(COMMON_FIELDS);
        if (type == Type.CONFIRMATION_REQUESTED) {
            expectedFields.add("requestedAt");
        } else {
            expectedFields.add("cancellationId");
        }
        if (!expectedFields.equals(fieldNames(root))) {
            throw new IllegalArgumentException("reservation resolution contains missing or unsupported fields");
        }

        String source = requiredIdentifier(root, "source");
        String resolutionId = requiredIdentifier(root, "resolutionId");
        UUID reservationId = requiredUuid(root, "reservationId");
        if (type == Type.CONFIRMATION_REQUESTED) {
            return new ReservationResolutionMessage(
                    type, source, resolutionId, reservationId, requiredInstant(root, "requestedAt"), null);
        }
        return new ReservationResolutionMessage(
                type, source, resolutionId, reservationId, null, requiredUuid(root, "cancellationId"));
    }

    public String payloadFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            addField(digest, "1");
            addField(digest, type.wireValue());
            addField(digest, source);
            addField(digest, resolutionId);
            addField(digest, reservationId.toString());
            addField(digest, requestedAt == null ? "" : requestedAt.toString());
            addField(digest, cancellationId == null ? "" : cancellationId.toString());
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void addField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static Set<String> fieldNames(JsonNode root) {
        return new HashSet<>(root.propertyNames());
    }

    private static String requiredIdentifier(JsonNode root, String fieldName) {
        String value = requiredText(root, fieldName);
        if (value.isBlank()
                || value.length() > 128
                || !value.equals(value.trim())
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + " must contain 1 to 128 non-padded characters");
        }
        return value;
    }

    private static String requiredText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException(fieldName + " must be a string");
        }
        return value.asString();
    }

    private static UUID requiredUuid(JsonNode root, String fieldName) {
        try {
            return UUID.fromString(requiredText(root, fieldName));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " must be a UUID", exception);
        }
    }

    private static Instant requiredInstant(JsonNode root, String fieldName) {
        try {
            return Instant.parse(requiredText(root, fieldName));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + " must be an ISO-8601 instant", exception);
        }
    }

    public enum Type {
        CONFIRMATION_REQUESTED("ReservationConfirmationRequested"),
        CANCELLATION_COMPLETED("ReservationCancellationCompleted");

        private final String wireValue;

        Type(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        private static Type fromWireValue(String wireValue) {
            for (Type type : values()) {
                if (type.wireValue.equals(wireValue)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("reservation resolution type is not supported");
        }
    }
}
