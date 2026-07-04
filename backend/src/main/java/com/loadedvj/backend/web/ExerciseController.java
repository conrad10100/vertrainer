package com.loadedvj.backend.web;

import com.loadedvj.backend.dto.ProgramDtos.*;
import com.loadedvj.backend.service.ProgramService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class ExerciseController {

    private final ProgramService programService;

    public ExerciseController(ProgramService programService) {
        this.programService = programService;
    }

    @PatchMapping("/api/exercises/{exerciseId}/log")
    public ExerciseResponse log(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exerciseId,
                                 @RequestBody LogExerciseRequest req) {
        return programService.logExercise(userId(jwt), exerciseId, req);
    }

    @PostMapping("/api/exercises/{exerciseId}/swap")
    public ExerciseResponse swap(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exerciseId,
                                  @Valid @RequestBody SwapExerciseRequest req) {
        return programService.swapExercise(userId(jwt), exerciseId, req);
    }

    @DeleteMapping("/api/exercises/{exerciseId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exerciseId) {
        programService.removeExercise(userId(jwt), exerciseId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/days/{dayId}/note")
    public DayResponse updateNote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID dayId,
                                   @RequestBody DayNoteRequest req) {
        return programService.updateDayNote(userId(jwt), dayId, req);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
