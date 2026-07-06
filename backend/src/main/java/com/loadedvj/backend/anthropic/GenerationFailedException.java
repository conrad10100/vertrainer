package com.loadedvj.backend.anthropic;

public class GenerationFailedException extends RuntimeException {
    public GenerationFailedException(String message) {
        super(message);
    }
}
