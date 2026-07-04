package com.loadedvj.backend.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "weeks")
public class Week {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(name = "cycle_position", nullable = false)
    private int cyclePosition;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Column(nullable = false)
    private String phase;

    @Column(name = "is_deload", nullable = false)
    private boolean deload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "week", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayIndex ASC")
    private List<Day> days = new ArrayList<>();

    public UUID getId() { return id; }
    public Program getProgram() { return program; }
    public void setProgram(Program program) { this.program = program; }
    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }
    public int getCyclePosition() { return cyclePosition; }
    public void setCyclePosition(int cyclePosition) { this.cyclePosition = cyclePosition; }
    public int getCycleNumber() { return cycleNumber; }
    public void setCycleNumber(int cycleNumber) { this.cycleNumber = cycleNumber; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public boolean isDeload() { return deload; }
    public void setDeload(boolean deload) { this.deload = deload; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Day> getDays() { return days; }

    public void addDay(Day day) {
        days.add(day);
        day.setWeek(this);
    }
}
