package com.loadedvj.backend.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "programs")
public class Program {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "program_name", nullable = false)
    private String programName;

    @Column(name = "current_vertical", nullable = false)
    private BigDecimal currentVertical;

    @Column(name = "target_vertical", nullable = false)
    private BigDecimal targetVertical;

    private BigDecimal height;
    private BigDecimal bodyweight;

    @Column(name = "days_per_week", nullable = false)
    private int daysPerWeek;

    @Column(name = "experience_level", nullable = false)
    private String experienceLevel;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("weekNumber ASC")
    private List<Week> weeks = new ArrayList<>();

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }
    public BigDecimal getCurrentVertical() { return currentVertical; }
    public void setCurrentVertical(BigDecimal currentVertical) { this.currentVertical = currentVertical; }
    public BigDecimal getTargetVertical() { return targetVertical; }
    public void setTargetVertical(BigDecimal targetVertical) { this.targetVertical = targetVertical; }
    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }
    public BigDecimal getBodyweight() { return bodyweight; }
    public void setBodyweight(BigDecimal bodyweight) { this.bodyweight = bodyweight; }
    public int getDaysPerWeek() { return daysPerWeek; }
    public void setDaysPerWeek(int daysPerWeek) { this.daysPerWeek = daysPerWeek; }
    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Week> getWeeks() { return weeks; }

    public void addWeek(Week week) {
        weeks.add(week);
        week.setProgram(this);
    }
}
