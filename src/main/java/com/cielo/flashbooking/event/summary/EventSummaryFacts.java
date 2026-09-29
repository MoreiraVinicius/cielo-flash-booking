package com.cielo.flashbooking.event.summary;

import java.time.Instant;
import java.util.UUID;

public record EventSummaryFacts(
        UUID eventId,
        String eventName,
        int capacity,
        Instant saleStartsAt,
        Instant endsAt,
        long acceptedReservations,
        long acceptedTickets,
        long validTicketsAtClose,
        long cancelledTicketsAtClose,
        long expiredTicketsAtClose,
        Instant peakMinute,
        Long peakTickets,
        Long firstFiveMinuteTickets,
        Instant firstAvailableZeroAt,
        Instant asOf,
        boolean complete) {}
