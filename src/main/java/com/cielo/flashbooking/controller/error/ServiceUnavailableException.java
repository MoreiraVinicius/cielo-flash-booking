package com.cielo.flashbooking.controller.error;

public final class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
