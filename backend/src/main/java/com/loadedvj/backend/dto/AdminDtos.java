package com.loadedvj.backend.dto;

public final class AdminDtos {

    private AdminDtos() { }

    public record UserUsageResponse(String email, int dailyCallLimit, int usedToday) { }
}
