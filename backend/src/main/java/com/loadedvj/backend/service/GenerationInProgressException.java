package com.loadedvj.backend.service;

public class GenerationInProgressException extends RuntimeException {
    public GenerationInProgressException(String message) {
        super(message);
    }
}
