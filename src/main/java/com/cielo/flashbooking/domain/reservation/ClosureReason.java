package com.cielo.flashbooking.domain.reservation;

public enum ClosureReason {
    CANCELLED_BY_REQUEST("Reserva cancelada por solicitação", ReservationStatus.CANCELLED),
    RESERVATION_DEADLINE_REACHED("Prazo da reserva encerrado", ReservationStatus.EXPIRED);

    private final String description;
    private final ReservationStatus terminalStatus;

    ClosureReason(String description, ReservationStatus terminalStatus) {
        this.description = description;
        this.terminalStatus = terminalStatus;
    }

    public String code() {
        return name();
    }

    public String description() {
        return description;
    }

    ReservationStatus terminalStatus() {
        return terminalStatus;
    }
}
