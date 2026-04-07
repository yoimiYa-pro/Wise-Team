package com.teampm.service;

import com.teampm.algo.SkillMatch;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final UserMapper userMapper;
    private final TaskMapper taskMapper;
    private final AhpService ahpService;
    private final TeamService teamService;
    private final TeamMemberMapper teamMemberMapper;

    public List<Map<String, Object>> recommend(Long teamId, String requiredSkillsJson, UserPrincipal actor) {
        var team = teamService.requireTeam(teamId);
        teamService.assertManager(actor, team);
        double[] w = ahpService.weightsOrDefault(teamId);
        List<Long> userIds = teamMemberMapper.findApprovedUserIds(teamId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long uid : userIds) {
            User u = userMapper.findById(uid);
            if (u == null || u.getStatus() == 0) {
                continue;
            }
            double s = SkillMatch.cosineSimilarity(requiredSkillsJson, u.getSkillsJson());
            BigDecimal rem = taskMapper.sumRemainingHoursByAssignee(uid);
            if (rem == null) {
                rem = BigDecimal.ZERO;
            }
            BigDecimal cap = u.getBaseCapacity() != null ? u.getBaseCapacity() : BigDecimal.valueOf(40);
            double load = cap.signum() == 0 ? 1 : rem.divide(cap, 4, RoundingMode.HALF_UP).doubleValue();
            load = Math.min(1.0, Math.max(0, load));
            double perf = u.getAvgPerformance() != null ? u.getAvgPerformance().doubleValue() / 100.0 : 0.75;
            perf = Math.min(1.0, Math.max(0, perf));
            double score = w[0] * s + w[1] * (1 - load) + w[2] * perf;
            out.add(Map.of(
                    "userId", uid,
                    "username", u.getUsername(),
                    "displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                    "skillMatch", round4(s),
                    "loadRatio", round4(load),
                    "performanceNorm", round4(perf),
                    "totalScore", round4(score)
            ));
        }
        out.sort(Comparator.comparingDouble(m -> -((Number) m.get("totalScore")).doubleValue()));
        return out;
    }

    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
