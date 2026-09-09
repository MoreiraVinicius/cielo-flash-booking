package com.cielo.flashbooking.application.error;

public final class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
