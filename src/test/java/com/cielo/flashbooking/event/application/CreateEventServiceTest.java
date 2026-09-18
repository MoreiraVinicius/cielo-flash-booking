package com.cielo.flashbooking.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.domain.event.Event;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateEventServiceTest {

    @Test
    void createsAndPersistsAnEventWithServerControlledValues() {
        EventWriter writer = mock(EventWriter.class);
        when(writer.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant now = Instant.parse("2026-09-09T12:00:00Z");
        when(writer.currentTime()).thenReturn(now);
        CreateEventService service = new CreateEventService(writer);

        Event result = service.create("  Flash sale  ", 50);

        ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
        verify(writer).save(saved.capture());
        assertThat(saved.getValue().id()).isNotNull();
        assertThat(saved.getValue().name()).isEqualTo("Flash sale");
        assertThat(saved.getValue().capacity()).isEqualTo(50);
        assertThat(saved.getValue().available()).isEqualTo(50);
        assertThat(saved.getValue().createdAt()).isEqualTo(now);
        assertThat(result).isSameAs(saved.getValue());
    }

    @Test
    void createsAnEventWithAnOptionalSaleWindowUsingPersistenceTime() {
        EventWriter writer = mock(EventWriter.class);
        when(writer.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant createdAt = Instant.parse("2026-09-09T12:00:00Z");
        Instant startsAt = createdAt.plusSeconds(60);
        Instant endsAt = startsAt.plusSeconds(60);
        when(writer.currentTime()).thenReturn(createdAt);
        CreateEventService service = new CreateEventService(writer);

        Event result = service.create("Flash sale", 50, startsAt, endsAt);

        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.startsAt()).isEqualTo(startsAt);
        assertThat(result.endsAt()).isEqualTo(endsAt);
    }
}
