package com.cielo.flashbooking.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.application.error.ResourceConflictException;
import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import com.cielo.flashbooking.domain.reservation.Customer;
import com.cielo.flashbooking.event.application.EventAvailabilityChanged;
import com.cielo.flashbooking.inventory.application.InventoryOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CreateReservationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ReservationWriter reservationWriter;

    @Mock
    private InventoryOperations inventoryOperations;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void useDatabaseTime() {
        when(reservationWriter.currentTime()).thenReturn(CLOCK.instant());
    }

    @Test
    void create_whenCapacityExists_persistsCustomerReservationAndOutbox() {
        UUID eventId = UUID.randomUUID();
        Customer customer = Customer.create(UUID.randomUUID(), "Ana", "ana@example.com", CLOCK.instant());
        when(reservationWriter.eventExists(eventId)).thenReturn(true);
        when(inventoryOperations.decrement(eventId, 2)).thenReturn(true);
        when(reservationWriter.upsertCustomer(any(Customer.class))).thenReturn(customer);
        CreateReservationService service = service();

        CreatedReservation result = service.create(eventId, 2, " Ana ", " ANA@EXAMPLE.COM ");

        assertThat(result.customer()).isEqualTo(customer);
        assertThat(result.reservation().eventId()).isEqualTo(eventId);
        assertThat(result.reservation().quantity()).isEqualTo(2);
        assertThat(result.reservation().expiresAt()).isEqualTo(CLOCK.instant().plus(Duration.ofMinutes(10)));
        verify(reservationWriter).save(result.reservation());
        verify(reservationWriter).addReservationCreatedOutboxEvent(result.reservation());
        verify(reservationWriter).addReservationExpirationScheduledOutboxEvent(result.reservation());
        verify(eventPublisher).publishEvent(any(EventAvailabilityChanged.class));
    }

    @Test
    void create_whenEventIsAbsent_doesNotChangeCapacityOrPersistAnything() {
        UUID eventId = UUID.randomUUID();
        when(reservationWriter.eventExists(eventId)).thenReturn(false);

        assertThatThrownBy(() -> service().create(eventId, 1, "Ana", "ana@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inventoryOperations, never()).decrement(any(), any(Integer.class));
        verify(reservationWriter, never()).upsertCustomer(any());
        verify(reservationWriter, never()).save(any());
        verify(reservationWriter, never()).addReservationCreatedOutboxEvent(any());
        verify(reservationWriter, never()).addReservationExpirationScheduledOutboxEvent(any());
    }

    @Test
    void create_whenCapacityIsInsufficient_doesNotPersistCustomerReservationOrOutbox() {
        UUID eventId = UUID.randomUUID();
        when(reservationWriter.eventExists(eventId)).thenReturn(true);
        when(inventoryOperations.decrement(eventId, 3)).thenReturn(false);

        assertThatThrownBy(() -> service().create(eventId, 3, "Ana", "ana@example.com"))
                .isInstanceOf(ResourceConflictException.class);

        verify(reservationWriter, never()).upsertCustomer(any());
        verify(reservationWriter, never()).save(any());
        verify(reservationWriter, never()).addReservationCreatedOutboxEvent(any());
        verify(reservationWriter, never()).addReservationExpirationScheduledOutboxEvent(any());
    }

    @Test
    void create_whenCustomerIsInvalid_doesNotBeginPersistenceOperations() {
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> service().create(eventId, 1, "", "not-an-email"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reservationWriter, never()).eventExists(any());
        verify(inventoryOperations, never()).decrement(any(), any(Integer.class));
    }

    private CreateReservationService service() {
        return new CreateReservationService(
                reservationWriter,
                inventoryOperations,
                new ReservationProperties(Duration.ofMinutes(10)),
                eventPublisher);
    }
}
