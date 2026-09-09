package com.cielo.flashbooking.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventTest {

    @Test
    void createsEventWithAllCapacityAvailable() {
        var event = Event.create(UUID.randomUUID(), "  Flash Sale  ", 50, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(event.name()).isEqualTo("Flash Sale");
        assertThat(event.capacity()).isEqualTo(50);
        assertThat(event.available()).isEqualTo(50);
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Event.create(UUID.randomUUID(), "Flash Sale", 0, Instant.now()));
    }

    @Test
    void rejectsBlankNameAfterTrimming() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Event.create(UUID.randomUUID(), "   ", 1, Instant.now()));
    }

    @Test
    void rejectsNameLongerThanTwoHundredCharacters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Event.create(UUID.randomUUID(), "a".repeat(201), 1, Instant.now()));
    }
}
