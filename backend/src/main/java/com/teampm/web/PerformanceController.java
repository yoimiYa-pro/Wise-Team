package com.teampm.web;

import com.teampm.domain.PerformanceCycle;
import com.teampm.domain.PerformanceReport;
import com.teampm.security.SecurityUtils;
import com.teampm.service.PerformanceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @GetMapping("/teams/{teamId}/cycles")
    public List<PerformanceCycle> cycles(@PathVariable Long teamId) {
        return performanceService.cycles(teamId, SecurityUtils.requireUser());
    }

    @PostMapping("/teams/{teamId}/cycles")
    public PerformanceCycle open(@PathVariable Long teamId, @RequestBody OpenCycleReq req) {
        return performanceService.openCycle(teamId, req.getCycleType(), req.getPeriodStart(), req.getPeriodEnd(),
                SecurityUtils.requireUser());
    }

    @PostMapping("/cycles/{cycleId}/close")
    public void close(@PathVariable Long cycleId, @RequestBody(required = false) CloseCycleReq req) {
        performanceService.closeCycle(cycleId, req != null ? req.getManagerRows() : null, SecurityUtils.requireUser());
    }

    @GetMapping("/users/{userId}/trend")
    public List<PerformanceReport> trend(@PathVariable Long userId) {
        return performanceService.trend(userId, SecurityUtils.requireUser());
    }

    @Data
    public static class OpenCycleReq {
        private String cycleType;
        private LocalDate periodStart;
        private LocalDate periodEnd;
    }

    @Data
    public static class CloseCycleReq {
        /** userId -> 4 维隶属度（优秀、良好、合格、不合格） */
        private Map<Long, List<Double>> managerRows;
    }
}
