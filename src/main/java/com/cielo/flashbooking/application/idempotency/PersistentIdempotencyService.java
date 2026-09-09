package com.cielo.flashbooking.application.idempotency;

import com.cielo.flashbooking.application.error.ResourceConflictException;
import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistentIdempotencyService {

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public PersistentIdempotencyService(IdempotencyStore idempotencyStore, ObjectMapper objectMapper) {
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IdempotencyResult execute(
            IdempotencyCommand command,
            Supplier<IdempotencyResponse> action,
            Function<RuntimeException, IdempotencyResponse> expectedFailureResponse) {
        if (!idempotencyStore.claim(command)) {
            return existingResult(command);
        }

        IdempotencyResponse response;
        try {
            response = action.get();
        } catch (ResourceNotFoundException | ResourceConflictException | IllegalArgumentException exception) {
            response = expectedFailureResponse.apply(exception);
        }
        idempotencyStore.complete(command.key(), response);
        return new IdempotencyResult(response.status(), serialize(response.body()));
    }

    private IdempotencyResult existingResult(IdempotencyCommand command) {
        IdempotencyStore.StoredIdempotencyResponse existing = idempotencyStore.findByKey(command.key())
                .orElseThrow(() -> new IllegalStateException("idempotency record disappeared"));
        if (!existing.matches(command)) {
            throw new ResourceConflictException("Idempotency-Key is incompatible with this request");
        }
        return new IdempotencyResult(existing.status(), existing.responseBody());
    }

    private String serialize(Object responseBody) {
        try {
            return objectMapper.writeValueAsString(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize idempotency response", exception);
        }
    }
}
