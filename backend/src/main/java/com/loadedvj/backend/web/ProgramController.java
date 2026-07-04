package com.loadedvj.backend.web;

import com.loadedvj.backend.dto.ProgramDtos.*;
import com.loadedvj.backend.service.ProgramService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/programs")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @PostMapping
    public ProgramResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProgramRequest req) {
        return programService.createProgram(userId(jwt), req);
    }

    @GetMapping("/active")
    public ProgramResponse active(@AuthenticationPrincipal Jwt jwt) {
        return programService.getActiveProgram(userId(jwt));
    }

    @PostMapping("/{programId}/weeks/next")
    public WeekResponse nextWeek(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID programId) {
        return programService.generateNextWeek(userId(jwt), programId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
