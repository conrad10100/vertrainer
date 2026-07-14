package com.loadedvj.backend.service;

public class WeekAlreadyGeneratedException extends RuntimeException {
    public WeekAlreadyGeneratedException(String message) {
        super(message);
    }
}
