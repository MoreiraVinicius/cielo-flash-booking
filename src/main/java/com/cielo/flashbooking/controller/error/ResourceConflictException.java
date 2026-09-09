package com.cielo.flashbooking.controller.error;

public final class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
