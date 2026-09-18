package com.cielo.flashbooking.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T00:10:00Z");

    @Test
    void normalizesCustomerEmailBeforeItIsExposedByTheDomain() {
        var customer = Customer.create(UUID.randomUUID(), "Ada", "  ADA@EXAMPLE.COM  ", CREATED_AT);

        assertThat(customer.email()).isEqualTo("ada@example.com");
    }

    @Test
    void rejectsInvalidCustomerEmail() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Customer.create(UUID.randomUUID(), "Ada", "not-an-email", CREATED_AT));
    }

    @Test
    void requiresAReservationCustomerAndEvent() {
        var customer = customer();

        assertThatNullPointerException()
                .isThrownBy(() -> Reservation.pending(UUID.randomUUID(), null, customer, 1, EXPIRES_AT, CREATED_AT));
        assertThatNullPointerException()
                .isThrownBy(() -> Reservation.pending(UUID.randomUUID(), UUID.randomUUID(), null, 1, EXPIRES_AT, CREATED_AT));
    }

    @Test
    void startsPendingBecauseTerminalTransitionsArePersistedAtomicallyByPostgresql() {
        var reservation = reservation();

        assertThat(reservation.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.closureReason()).isNull();
        assertThat(reservation.updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void rejectsNonPositiveReservationQuantity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Reservation.pending(UUID.randomUUID(), UUID.randomUUID(), customer(), 0, EXPIRES_AT, CREATED_AT));
    }

    private Customer customer() {
        return Customer.create(UUID.randomUUID(), "Ada", "ada@example.com", CREATED_AT);
    }

    private Reservation reservation() {
        return Reservation.pending(UUID.randomUUID(), UUID.randomUUID(), customer(), 2, EXPIRES_AT, CREATED_AT);
    }
}
