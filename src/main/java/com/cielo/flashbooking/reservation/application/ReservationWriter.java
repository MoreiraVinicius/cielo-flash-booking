package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.domain.reservation.Customer;
import com.cielo.flashbooking.domain.reservation.Reservation;
import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReservationWriter {

    /** Locks an existing reservation before the inbox takes its foreign-key reference to that row. */
    void lockReservationForResolution(UUID reservationId);

    Instant currentTime();

    boolean eventExists(UUID eventId);

    Customer upsertCustomer(Customer customer);

    void save(Reservation reservation);

    void addReservationCreatedOutboxEvent(Reservation reservation);

    void addReservationHeldOutboxEvent(Reservation reservation);

    void addReservationExpirationScheduledOutboxEvent(Reservation reservation);

    void addReservationHoldClosedOutboxEvent(UUID reservationId, UUID eventId, int quantity, ReservationStatus status);

    void addReservationCancellationRequestedOutboxEvent(CancellationRequest request);

    void addReservationConfirmationResultOutboxEvent(
            UUID reservationId,
            String resolutionId,
            String resultType,
            String resultCode,
            ReservationStatus status,
            Instant decidedAt);

    /**
     * Closes PENDING as CANCELLED before the deadline or EXPIRED at/after it, using database time after the row lock.
     * Returns a release only for the winning transition; participates in the caller's capacity-return transaction.
     */
    Optional<CapacityRelease> closePendingOnCancellation(UUID reservationId);

    Optional<CapacityRelease> expirePending(UUID reservationId);

    /** Locks the reservation, then decides whether confirmation or expiry wins using PostgreSQL time. */
    Optional<ConfirmationTransition> confirmPending(UUID reservationId);

    /** Starts cancellation only if the reservation is still CONFIRMED. */
    Optional<CancellationRequest> requestConfirmedCancellation(UUID reservationId, UUID cancellationId);

    /** Completes cancellation only when the supplied id matches the pending request. */
    Optional<CancellationCompletionTransition> completeConfirmedCancellation(UUID reservationId, UUID cancellationId);

    record CapacityRelease(UUID eventId, int quantity, ReservationStatus closedStatus) {}

    record ConfirmationTransition(
            ReservationStatus status, Instant confirmedAt, CapacityRelease expiredCapacityRelease, boolean changed) {}

    record CancellationRequest(UUID reservationId, UUID eventId, int quantity, UUID cancellationId) {}

    record CancellationCompletionTransition(
            ReservationStatus status, UUID cancellationId, boolean changed, CapacityRelease capacityRelease) {}
}
