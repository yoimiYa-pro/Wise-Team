package com.teampm.service;

import com.teampm.domain.Task;
import com.teampm.domain.User;
import com.teampm.mapper.TaskMapper;
import com.teampm.mapper.TeamMemberMapper;
import com.teampm.mapper.UserMapper;
import com.teampm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TaskMapper taskMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final TeamService teamService;

    public Map<String, Object> teamDashboard(Long teamId, UserPrincipal actor) {
        var team = teamService.requireTeam(teamId);
        if ("MANAGER".equals(actor.getRole())) {
            teamService.assertManager(actor, team);
        } else if (!"ADMIN".equals(actor.getRole())) {
            teamService.assertMemberApproved(actor, teamId);
        }
        List<Task> tasks = taskMapper.findByTeamId(teamId);
        Map<String, Long> pie = new HashMap<>();
        for (Task t : tasks) {
            pie.merge(t.getStatus(), 1L, Long::sum);
        }
        List<Long> uids = teamMemberMapper.findApprovedUserIds(teamId);
        List<Map<String, Object>> loadBars = new ArrayList<>();
        for (Long uid : uids) {
            User u = userMapper.findById(uid);
            BigDecimal rem = taskMapper.sumRemainingHoursByAssignee(uid);
            if (rem == null) {
                rem = BigDecimal.ZERO;
            }
            BigDecimal cap = u != null && u.getBaseCapacity() != null ? u.getBaseCapacity() : BigDecimal.valueOf(40);
            double ratio = cap.signum() == 0 ? 0 : rem.divide(cap, 4, RoundingMode.HALF_UP).doubleValue();
            String displayName = "";
            if (u != null) {
                String dn = u.getDisplayName();
                displayName = (dn != null && !dn.isBlank()) ? dn : u.getUsername();
            }
            loadBars.add(Map.of(
                    "userId", uid,
                    "username", u != null ? u.getUsername() : "",
                    "displayName", displayName,
                    "remainingHours", rem.doubleValue(),
                    "loadRatio", ratio
            ));
        }
        List<Map<String, Object>> riskRadar = new ArrayList<>();
        for (Task t : taskMapper.findInProgressOrCreatedByTeam(teamId)) {
            riskRadar.add(Map.of(
                    "taskId", t.getId(),
                    "title", t.getTitle(),
                    "riskLevel", t.getRiskLevel(),
                    "delayProbability", t.getDelayProbability() != null ? t.getDelayProbability().doubleValue() : 0
            ));
        }
        return Map.of(
                "taskStatusPie", pie,
                "memberLoad", loadBars,
                "riskTasks", riskRadar
        );
    }
}
