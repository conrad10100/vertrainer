package com.loadedvj.backend.service;

import com.loadedvj.backend.domain.Exercise;
import com.loadedvj.backend.domain.VerticalCheckin;
import com.loadedvj.backend.dto.ProgramDtos.*;
import com.loadedvj.backend.repository.ExerciseRepository;
import com.loadedvj.backend.repository.VerticalCheckinRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Pattern NUMERIC = Pattern.compile("[\\d.]+");

    private final ExerciseRepository exerciseRepository;
    private final VerticalCheckinRepository checkinRepository;

    public DashboardService(ExerciseRepository exerciseRepository, VerticalCheckinRepository checkinRepository) {
        this.exerciseRepository = exerciseRepository;
        this.checkinRepository = checkinRepository;
    }

    public List<String> listExerciseNames(UUID userId) {
        return exerciseRepository.findDistinctExerciseNamesForUser(userId);
    }

    public DashboardResponse getDashboard(UUID userId, String exerciseName) {
        List<CheckinPoint> verticalHistory = checkinRepository.findByUserIdOrderByRecordedAtAsc(userId).stream()
            .map(this::toCheckinPoint)
            .toList();

        List<LiftPoint> liftProgression = exerciseName == null || exerciseName.isBlank()
            ? List.of()
            : exerciseRepository.findByUserIdAndName(userId, exerciseName).stream()
                .map(this::toLiftPoint)
                .filter(p -> p.targetWeight() != null)
                .toList();

        List<AdherencePoint> adherence = buildAdherence(userId);

        return new DashboardResponse(verticalHistory, liftProgression, adherence);
    }

    private List<AdherencePoint> buildAdherence(UUID userId) {
        List<Exercise> all = exerciseRepository.findAllForUser(userId);
        Map<Integer, int[]> counts = new TreeMap<>(); // weekNumber -> [logged, total]
        for (Exercise ex : all) {
            int weekNumber = ex.getDay().getWeek().getWeekNumber();
            int[] bucket = counts.computeIfAbsent(weekNumber, k -> new int[2]);
            bucket[1]++;
            if (ex.getLoggedWeight() != null || ex.getLoggedReps() != null) {
                bucket[0]++;
            }
        }
        return counts.entrySet().stream()
            .map(e -> new AdherencePoint(e.getKey(), e.getValue()[1] == 0 ? 0.0 : (double) e.getValue()[0] / e.getValue()[1]))
            .sorted(Comparator.comparingInt(AdherencePoint::weekNumber))
            .toList();
    }

    private CheckinPoint toCheckinPoint(VerticalCheckin c) {
        return new CheckinPoint(c.getRecordedAt(), c.getInches());
    }

    private LiftPoint toLiftPoint(Exercise ex) {
        BigDecimal weight = parseNumeric(ex.getTargetWeight());
        return new LiftPoint(ex.getDay().getWeek().getWeekNumber(), weight);
    }

    private BigDecimal parseNumeric(String value) {
        if (value == null) return null;
        Matcher m = NUMERIC.matcher(value);
        if (!m.find()) return null;
        try {
            return new BigDecimal(m.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
