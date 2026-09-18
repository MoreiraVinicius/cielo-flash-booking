package com.cielo.flashbooking.domain.reservation;

public enum ClosureReason {
    CANCELLED_BY_REQUEST("Reserva cancelada por solicitação"),
    RESERVATION_DEADLINE_REACHED("Prazo da reserva encerrado");

    private final String description;

    ClosureReason(String description) {
        this.description = description;
    }

    public String code() {
        return name();
    }

    public String description() {
        return description;
    }
}
