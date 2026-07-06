package com.loadedvj.backend.web;

import com.loadedvj.backend.anthropic.GenerationFailedException;
import com.loadedvj.backend.service.DailyLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DailyLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleDailyLimitExceeded(DailyLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(GenerationFailedException.class)
    public ResponseEntity<Map<String, String>> handleGenerationFailed(GenerationFailedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", "AI generation failed, please try again (" + ex.getMessage() + ")"));
    }
}
