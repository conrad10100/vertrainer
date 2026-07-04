package com.loadedvj.backend.web;

import com.loadedvj.backend.domain.VerticalCheckin;
import com.loadedvj.backend.dto.ProgramDtos.CheckinRequest;
import com.loadedvj.backend.dto.ProgramDtos.CheckinResponse;
import com.loadedvj.backend.repository.VerticalCheckinRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkins")
public class CheckinController {

    private final VerticalCheckinRepository checkinRepository;

    public CheckinController(VerticalCheckinRepository checkinRepository) {
        this.checkinRepository = checkinRepository;
    }

    @PostMapping
    public CheckinResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckinRequest req) {
        VerticalCheckin checkin = new VerticalCheckin();
        checkin.setUserId(UUID.fromString(jwt.getSubject()));
        checkin.setInches(req.inches());
        checkin.setRecordedAt(req.recordedAt() != null ? req.recordedAt() : Instant.now());
        checkin.setNotes(req.notes());
        VerticalCheckin saved = checkinRepository.save(checkin);
        return new CheckinResponse(saved.getId(), saved.getInches(), saved.getRecordedAt(), saved.getNotes());
    }
}
