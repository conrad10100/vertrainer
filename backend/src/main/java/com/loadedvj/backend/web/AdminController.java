package com.loadedvj.backend.web;

import com.loadedvj.backend.dto.AdminDtos.UserUsageResponse;
import com.loadedvj.backend.dto.ProgramDtos.DashboardResponse;
import com.loadedvj.backend.service.AdminService;
import com.loadedvj.backend.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final DashboardService dashboardService;

    public AdminController(AdminService adminService, DashboardService dashboardService) {
        this.adminService = adminService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/usage")
    public List<UserUsageResponse> usage(@AuthenticationPrincipal Jwt jwt) {
        adminService.requireAdmin(jwt);
        return adminService.getUsageSummary();
    }

    @GetMapping("/users/{userId}/dashboard/exercises")
    public List<String> userDashboardExercises(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        adminService.requireAdmin(jwt);
        return dashboardService.listExerciseNames(userId);
    }

    @GetMapping("/users/{userId}/dashboard")
    public DashboardResponse userDashboard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                                            @RequestParam(required = false) String exercise) {
        adminService.requireAdmin(jwt);
        return dashboardService.getDashboard(userId, exercise);
    }
}
