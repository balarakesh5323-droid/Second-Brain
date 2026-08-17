package com.secondbrain.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, UUID id) {
        super(message + " with id: " + id);
    }
}
