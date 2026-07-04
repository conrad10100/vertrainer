package com.loadedvj.backend.web;

import com.loadedvj.backend.dto.ProgramDtos.DashboardResponse;
import com.loadedvj.backend.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard/exercises")
    public List<String> exerciseNames(@AuthenticationPrincipal Jwt jwt) {
        return dashboardService.listExerciseNames(userId(jwt));
    }

    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(required = false) String exercise) {
        return dashboardService.getDashboard(userId(jwt), exercise);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
