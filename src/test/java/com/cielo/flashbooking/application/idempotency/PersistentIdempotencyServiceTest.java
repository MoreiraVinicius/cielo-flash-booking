package com.cielo.flashbooking.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.application.error.ResourceConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersistentIdempotencyServiceTest {

    @Mock
    private IdempotencyStore idempotencyStore;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void execute_whenKeyIsNew_persistsAndReturnsTheFinalResponse() {
        IdempotencyCommand command = command("key-1", Map.of("capacity", 10));
        when(idempotencyStore.claim(command)).thenReturn(true);

        IdempotencyResult result = service().execute(
                command,
                () -> new IdempotencyResponse(201, Map.of("id", "event-1")),
                exception -> throwUnexpected(exception));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.responseBody()).isEqualTo("{\"id\":\"event-1\"}");
        ArgumentCaptor<IdempotencyResponse> responseCaptor = ArgumentCaptor.forClass(IdempotencyResponse.class);
        verify(idempotencyStore).complete(eq(command.key()), responseCaptor.capture());
        assertThat(responseCaptor.getValue().status()).isEqualTo(201);
        assertThat(responseCaptor.getValue().body()).isEqualTo(Map.of("id", "event-1"));
    }

    @Test
    void execute_whenKeyMatchesExistingRequest_returnsStoredResultWithoutExecutingAgain() {
        IdempotencyCommand command = command("key-1", Map.of("capacity", 10));
        when(idempotencyStore.claim(command)).thenReturn(false);
        when(idempotencyStore.findByKey(command.key())).thenReturn(java.util.Optional.of(
                new IdempotencyStore.StoredIdempotencyResponse(
                        command.operation(), command.normalizedTarget(), command.payloadHash(), 201, "{\"id\":\"event-1\"}")));

        IdempotencyResult result = service().execute(
                command,
                () -> {
                    throw new AssertionError("the command must not execute twice");
                },
                exception -> throwUnexpected(exception));

        assertThat(result).isEqualTo(new IdempotencyResult(201, "{\"id\":\"event-1\"}"));
        verify(idempotencyStore, never()).complete(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void execute_whenKeyIsReusedForDifferentPayload_rejectsItWithoutExecuting() {
        IdempotencyCommand original = command("key-1", Map.of("capacity", 10));
        IdempotencyCommand changed = command("key-1", Map.of("capacity", 11));
        when(idempotencyStore.claim(changed)).thenReturn(false);
        when(idempotencyStore.findByKey(changed.key())).thenReturn(java.util.Optional.of(
                new IdempotencyStore.StoredIdempotencyResponse(
                        original.operation(), original.normalizedTarget(), original.payloadHash(), 201, "{}")));

        assertThatThrownBy(() -> service().execute(
                changed,
                () -> new IdempotencyResponse(201, Map.of()),
                exception -> throwUnexpected(exception)))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void execute_whenDomainConflictOccurs_persistsTheFinalConflictResponse() {
        IdempotencyCommand command = command("key-1", Map.of("quantity", 2));
        when(idempotencyStore.claim(command)).thenReturn(true);

        IdempotencyResult result = service().execute(
                command,
                () -> {
                    throw new ResourceConflictException("capacity");
                },
                exception -> new IdempotencyResponse(409, Map.of("code", "resource-conflict")));

        assertThat(result.status()).isEqualTo(409);
        assertThat(result.responseBody()).contains("resource-conflict");
        verify(idempotencyStore).complete(command.key(), new IdempotencyResponse(409, Map.of("code", "resource-conflict")));
    }

    @Test
    void execute_whenUnexpectedFailureOccurs_doesNotCompleteTheRecord() {
        IdempotencyCommand command = command("key-1", Map.of("capacity", 10));
        when(idempotencyStore.claim(command)).thenReturn(true);

        assertThatThrownBy(() -> service().execute(
                command,
                () -> {
                    throw new IllegalStateException("database unavailable");
                },
                exception -> throwUnexpected(exception)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(idempotencyStore, never()).complete(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    private PersistentIdempotencyService service() {
        return new PersistentIdempotencyService(idempotencyStore, objectMapper);
    }

    private IdempotencyCommand command(String key, Map<String, Integer> payload) {
        return IdempotencyCommand.from(key, "POST", "/events", payload, objectMapper);
    }

    private IdempotencyResponse throwUnexpected(RuntimeException exception) {
        throw exception;
    }
}
