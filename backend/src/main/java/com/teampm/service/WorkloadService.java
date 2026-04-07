package com.teampm.service;

import com.teampm.config.AppProperties;
import com.teampm.algo.ExponentialSmoothing;
import com.teampm.domain.Task;
import com.teampm.domain.WorkloadWeekly;
import com.teampm.exception.ApiException;
import com.teampm.mapper.TaskMapper;
import com.teampm.mapper.WorkloadWeeklyMapper;
import com.teampm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkloadService {

    private final WorkloadWeeklyMapper workloadWeeklyMapper;
    private final TaskMapper taskMapper;
    private final TeamService teamService;
    private final SystemConfigService systemConfigService;
    private final AppProperties appProperties;

    public static String isoWeek(LocalDate d) {
        int y = d.get(IsoFields.WEEK_BASED_YEAR);
        int w = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return y + "-W" + (w < 10 ? "0" + w : w);
    }

    @Transactional
    public void logWeekHours(Long teamId, BigDecimal hours, UserPrincipal actor) {
        teamService.assertMemberApproved(actor, teamId);
        String yw = isoWeek(LocalDate.now());
        WorkloadWeekly row = new WorkloadWeekly();
        row.setUserId(actor.getId());
        row.setTeamId(teamId);
        row.setYearWeek(yw);
        row.setActualHours(hours != null ? hours : BigDecimal.ZERO);
        workloadWeeklyMapper.upsert(row);
    }

    public Map<String, Object> forecastForUser(Long teamId, Long userId, UserPrincipal actor) {
        teamService.requireTeam(teamId);
        if (!actor.getId().equals(userId) && !"ADMIN".equals(actor.getRole())) {
            if ("MANAGER".equals(actor.getRole())) {
                teamService.assertManager(actor, teamService.requireTeam(teamId));
            } else {
                throw new ApiException(HttpStatus.FORBIDDEN, "无权查看");
            }
        }
        double alpha = parseAlpha();
        List<WorkloadWeekly> hist = workloadWeeklyMapper.findByUserIdOrderByWeek(userId, 24);
        List<Double> series = new LinkedList<>();
        for (int i = hist.size() - 1; i >= 0; i--) {
            series.add(hist.get(i).getActualHours().doubleValue());
        }
        double lastF = series.isEmpty() ? 0 : series.get(series.size() - 1);
        double nextBase = ExponentialSmoothing.nextForecast(series, alpha, lastF);

        List<Map<String, Object>> weeks = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int k = 1; k <= 4; k++) {
            LocalDate weekStart = today.plusWeeks(k).with(java.time.DayOfWeek.MONDAY);
            String yw = isoWeek(weekStart);
            double scheduled = sumScheduledHoursForWeek(userId, weekStart);
            weeks.add(Map.of(
                    "yearWeek", yw,
                    "smoothedBase", round2(nextBase),
                    "scheduledFromTasks", round2(scheduled),
                    "totalEstimate", round2(nextBase + scheduled)
            ));
        }
        return Map.of("historyWeeks", hist, "alpha", alpha, "nextWeekSmoothed", round2(nextBase), "forecast", weeks);
    }

    private double sumScheduledHoursForWeek(Long userId, LocalDate weekMonday) {
        LocalDate weekEnd = weekMonday.plusDays(6);
        List<Task> tasks = taskMapper.findByAssigneeId(userId);
        double sum = 0;
        for (Task t : tasks) {
            if (t.getDeadline() == null) {
                continue;
            }
            if (!t.getDeadline().isBefore(weekMonday) && !t.getDeadline().isAfter(weekEnd)) {
                if (List.of("CREATED", "IN_PROGRESS", "SUSPENDED").contains(t.getStatus()) && t.getEstHours() != null) {
                    double rem = t.getEstHours().doubleValue() * (1 - (t.getProgress() != null ? t.getProgress() : 0) / 100.0);
                    sum += Math.max(0, rem);
                }
            }
        }
        return sum;
    }

    private double parseAlpha() {
        String v = systemConfigService.get("load.smoothing.alpha");
        if (v != null) {
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException ignored) {
            }
        }
        return appProperties.getLoad().getSmoothingAlpha();
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
