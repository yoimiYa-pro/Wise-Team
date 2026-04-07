package com.teampm.web;

import com.teampm.security.SecurityUtils;
import com.teampm.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/teams/{teamId}")
    public Map<String, Object> team(@PathVariable Long teamId) {
        return dashboardService.teamDashboard(teamId, SecurityUtils.requireUser());
    }
}
