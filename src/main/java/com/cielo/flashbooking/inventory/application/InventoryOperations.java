package com.cielo.flashbooking.inventory.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InventoryOperations {

    Optional<Instant> decrement(UUID eventId, int quantity);

    boolean increment(UUID eventId, int quantity);
}
