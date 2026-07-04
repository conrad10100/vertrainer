package com.loadedvj.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "user_limits")
public class UserLimit {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "daily_call_limit", nullable = false)
    private int dailyCallLimit;

    protected UserLimit() { }

    public UserLimit(UUID userId, int dailyCallLimit) {
        this.userId = userId;
        this.dailyCallLimit = dailyCallLimit;
    }

    public UUID getUserId() { return userId; }
    public int getDailyCallLimit() { return dailyCallLimit; }
}
