package com.cielo.flashbooking.inventory.application;

import java.util.UUID;

public interface InventoryOperations {

    boolean decrement(UUID eventId, int quantity);

    boolean increment(UUID eventId, int quantity);
}
