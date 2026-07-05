package com.loadedvj.backend.dto;

public final class AdminDtos {

    private AdminDtos() { }

    public record UserUsageResponse(String userId, String email, int dailyCallLimit, int usedToday) { }
}
