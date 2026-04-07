package com.teampm.web;

import com.teampm.security.SecurityUtils;
import com.teampm.service.WorkloadService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/workload")
@RequiredArgsConstructor
public class WorkloadController {

    private final WorkloadService workloadService;

    @PostMapping("/teams/{teamId}/log")
    public void log(@PathVariable Long teamId, @RequestBody HoursReq req) {
        workloadService.logWeekHours(teamId, req.getHours(), SecurityUtils.requireUser());
    }

    @GetMapping("/teams/{teamId}/forecast")
    public Map<String, Object> forecast(@PathVariable Long teamId, @RequestParam Long userId) {
        return workloadService.forecastForUser(teamId, userId, SecurityUtils.requireUser());
    }

    @Data
    public static class HoursReq {
        private BigDecimal hours;
    }
}
