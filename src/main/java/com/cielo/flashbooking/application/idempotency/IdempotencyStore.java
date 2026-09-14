package com.cielo.flashbooking.application.idempotency;

import java.util.Optional;

public interface IdempotencyStore {

    boolean claim(IdempotencyCommand command);

    Optional<StoredIdempotencyResponse> findByKey(String key);

    void complete(String key, IdempotencyResponse response);

    int deleteExpired(int limit);

    record StoredIdempotencyResponse(
            String operation, String normalizedTarget, String payloadHash, int status, String responseBody) {

        public boolean matches(IdempotencyCommand command) {
            return operation.equals(command.operation())
                    && normalizedTarget.equals(command.normalizedTarget())
                    && payloadHash.equals(command.payloadHash());
        }
    }
}
