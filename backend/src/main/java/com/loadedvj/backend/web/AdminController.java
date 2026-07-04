package com.loadedvj.backend.web;

import com.loadedvj.backend.dto.AdminDtos.UserUsageResponse;
import com.loadedvj.backend.service.AdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/usage")
    public List<UserUsageResponse> usage(@AuthenticationPrincipal Jwt jwt) {
        adminService.requireAdmin(jwt);
        return adminService.getUsageSummary();
    }
}
