package com.teampm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampm.algo.FuzzyComprehensiveEvaluation;
import com.teampm.domain.PerformanceCycle;
import com.teampm.domain.PerformanceReport;
import com.teampm.domain.PeerReview;
import com.teampm.domain.Task;
import com.teampm.domain.User;
import com.teampm.exception.ApiException;
import com.teampm.mapper.TeamMemberMapper;
import com.teampm.mapper.PeerReviewMapper;
import com.teampm.mapper.PerformanceCycleMapper;
import com.teampm.mapper.PerformanceReportMapper;
import com.teampm.mapper.TaskMapper;
import com.teampm.mapper.UserMapper;
import com.teampm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceCycleMapper performanceCycleMapper;
    private final PerformanceReportMapper performanceReportMapper;
    private final PeerReviewMapper peerReviewMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamService teamService;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public List<PerformanceCycle> cycles(Long teamId, UserPrincipal actor) {
        var team = teamService.requireTeam(teamId);
        teamService.assertManager(actor, team);
        return performanceCycleMapper.findByTeamId(teamId);
    }

    @Transactional
    public PerformanceCycle openCycle(Long teamId, String type, java.time.LocalDate start, java.time.LocalDate end, UserPrincipal actor) {
        var team = teamService.requireTeam(teamId);
        teamService.assertManager(actor, team);
        PerformanceCycle c = new PerformanceCycle();
        c.setTeamId(teamId);
        c.setCycleType(type);
        c.setPeriodStart(start);
        c.setPeriodEnd(end);
        c.setClosedFlag(0);
        performanceCycleMapper.insert(c);
        auditService.log(actor.getId(), "PERF_CYCLE_OPEN", "PerformanceCycle", c.getId(), type);
        return performanceCycleMapper.findById(c.getId());
    }

    @Transactional
    public void submitPeerReview(Long teamId, Long cycleId, Long targetUserId, Map<String, Double> dimensions, UserPrincipal actor) {
        teamService.assertMemberApproved(actor, teamId);
        PerformanceCycle c = performanceCycleMapper.findById(cycleId);
        if (c == null || !c.getTeamId().equals(teamId) || c.getClosedFlag() != null && c.getClosedFlag() == 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "绩效周期无效或已关闭");
        }
        if (targetUserId.equals(actor.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能评价自己");
        }
        if (peerReviewMapper.countByReviewerInCycle(cycleId, actor.getId(), targetUserId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "本轮已评价过该同事");
        }
        PeerReview r = new PeerReview();
        r.setTeamId(teamId);
        r.setCycleId(cycleId);
        r.setTargetUserId(targetUserId);
        r.setReviewerUserId(actor.getId());
        try {
            r.setDimensionScoresJson(objectMapper.writeValueAsString(dimensions));
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "评分格式错误");
        }
        peerReviewMapper.insert(r);
    }

    @Transactional
    public void closeCycle(Long cycleId, Map<Long, List<Double>> managerRows, UserPrincipal actor) {
        PerformanceCycle c = performanceCycleMapper.findById(cycleId);
        if (c == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "周期不存在");
        }
        var team = teamService.requireTeam(c.getTeamId());
        teamService.assertManager(actor, team);
        if (c.getClosedFlag() != null && c.getClosedFlag() == 1) {
            throw new ApiException(HttpStatus.CONFLICT, "周期已关闭");
        }
        double wm = parseW("fce.weights.manager", 0.4);
        double ws = parseW("fce.weights.system", 0.35);
        double wp = parseW("fce.weights.peer", 0.25);
        double sum = wm + ws + wp;
        wm /= sum;
        ws /= sum;
        wp /= sum;
        double[] w = new double[]{wm, ws, wp};

        List<Long> members = teamMemberMapper.findApprovedUserIds(c.getTeamId());
        for (Long uid : members) {
            double[] mgr = normalizeFour(managerRows != null ? managerRows.get(uid) : null);
            double[] sys = systemRow(uid, c);
            double[] peer = peerRow(c.getId(), uid);
            double[][] r = new double[][]{mgr, sys, peer};
            double score = FuzzyComprehensiveEvaluation.score(r, w);
            PerformanceReport rep = new PerformanceReport();
            rep.setCycleId(cycleId);
            rep.setUserId(uid);
            rep.setScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
            try {
                rep.setDetailJson(objectMapper.writeValueAsString(Map.of(
                        "manager", mgr,
                        "system", sys,
                        "peer", peer,
                        "weights", w
                )));
            } catch (JsonProcessingException e) {
                rep.setDetailJson("{}");
            }
            performanceReportMapper.upsert(rep);
            User u = userMapper.findById(uid);
            if (u != null && u.getAvgPerformance() != null) {
                double old = u.getAvgPerformance().doubleValue();
                double blended = old * 0.7 + score * 0.3;
                userMapper.updatePerformance(uid, BigDecimal.valueOf(blended).setScale(2, RoundingMode.HALF_UP));
            } else if (u != null) {
                userMapper.updatePerformance(uid, BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
            }
        }
        performanceCycleMapper.closeCycle(cycleId);
        auditService.log(actor.getId(), "PERF_CYCLE_CLOSE", "PerformanceCycle", cycleId, null);
    }

    private double parseW(String key, double def) {
        String v = systemConfigService.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double[] normalizeFour(List<Double> row) {
        double[] d = new double[]{0.25, 0.25, 0.25, 0.25};
        if (row == null || row.size() != 4) {
            return d;
        }
        double s = 0;
        for (int i = 0; i < 4; i++) {
            double v = Math.max(0, row.get(i));
            d[i] = v;
            s += v;
        }
        if (s <= 0) {
            return new double[]{0.25, 0.25, 0.25, 0.25};
        }
        for (int i = 0; i < 4; i++) {
            d[i] /= s;
        }
        return d;
    }

    private double[] systemRow(Long userId, PerformanceCycle c) {
        List<Task> tasks = taskMapper.findByAssigneeId(userId);
        int total = 0;
        int ok = 0;
        for (Task t : tasks) {
            if (t.getDeadline() == null) {
                continue;
            }
            if (t.getDeadline().isBefore(c.getPeriodStart()) || t.getDeadline().isAfter(c.getPeriodEnd())) {
                continue;
            }
            if (!"COMPLETED".equals(t.getStatus()) && !"ARCHIVED".equals(t.getStatus())) {
                continue;
            }
            total++;
            if (t.getUpdatedAt() != null) {
                java.time.LocalDate done = t.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                if (!done.isAfter(t.getDeadline())) {
                    ok++;
                }
            } else {
                ok++;
            }
        }
        double ratio = total == 0 ? 0.75 : (double) ok / total;
        if (ratio >= 0.9) {
            return new double[]{0.55, 0.35, 0.08, 0.02};
        }
        if (ratio >= 0.7) {
            return new double[]{0.2, 0.5, 0.25, 0.05};
        }
        if (ratio >= 0.5) {
            return new double[]{0.05, 0.25, 0.5, 0.2};
        }
        return new double[]{0.02, 0.08, 0.35, 0.55};
    }

    private double[] peerRow(Long cycleId, Long targetUserId) {
        List<PeerReview> list = peerReviewMapper.findByCycleAndTarget(cycleId, targetUserId);
        if (list.isEmpty()) {
            return new double[]{0.25, 0.25, 0.25, 0.25};
        }
        double[] acc = new double[4];
        int n = 0;
        for (PeerReview pr : list) {
            double avg = averageDimensionScore(pr.getDimensionScoresJson());
            acc[0] += scoreToMemb(avg, 0);
            acc[1] += scoreToMemb(avg, 1);
            acc[2] += scoreToMemb(avg, 2);
            acc[3] += scoreToMemb(avg, 3);
            n++;
        }
        for (int i = 0; i < 4; i++) {
            acc[i] /= n;
        }
        double s = acc[0] + acc[1] + acc[2] + acc[3];
        if (s <= 0) {
            return new double[]{0.25, 0.25, 0.25, 0.25};
        }
        for (int i = 0; i < 4; i++) {
            acc[i] /= s;
        }
        return acc;
    }

    private double averageDimensionScore(String json) {
        if (json == null || json.isBlank()) {
            return 3.5;
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, Map.class);
            double sum = 0;
            int c = 0;
            for (Object v : m.values()) {
                double d = v instanceof Number ? ((Number) v).doubleValue() : Double.parseDouble(String.valueOf(v));
                sum += d;
                c++;
            }
            return c == 0 ? 3.5 : sum / c;
        } catch (Exception e) {
            return 3.5;
        }
    }

    private static double scoreToMemb(double score, int gradeIndex) {
        double[] template;
        if (score >= 4.5) {
            template = new double[]{0.65, 0.28, 0.05, 0.02};
        } else if (score >= 3.8) {
            template = new double[]{0.25, 0.5, 0.2, 0.05};
        } else if (score >= 3.0) {
            template = new double[]{0.08, 0.25, 0.5, 0.17};
        } else {
            template = new double[]{0.02, 0.08, 0.3, 0.6};
        }
        return template[gradeIndex];
    }

    public List<PerformanceReport> trend(Long userId, UserPrincipal actor) {
        if (!actor.getId().equals(userId) && !"ADMIN".equals(actor.getRole()) && !"MANAGER".equals(actor.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权查看");
        }
        return performanceReportMapper.findByUserId(userId, 24);
    }
}
