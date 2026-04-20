package com.teampm.service;

import com.teampm.config.AppProperties;
import com.teampm.domain.Task;
import com.teampm.domain.TaskDependency;
import com.teampm.domain.Team;
import com.teampm.domain.User;
import com.teampm.algo.RiskModel;
import com.teampm.exception.ApiException;
import com.teampm.mapper.TaskDependencyMapper;
import com.teampm.mapper.TaskMapper;
import com.teampm.mapper.UserMapper;
import com.teampm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务生命周期：创建与依赖、指派、进度与风险、审计与站内信；部分规则与 {@link com.teampm.algo.RiskModel} 联动。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    /** 站内信：任务风险跨阈值上升，通知团队管理者 */
    public static final String MSG_TASK_RISK_ESCALATION = "TASK_RISK_ESCALATION";
    /** 站内信：负责人上报任务进度（无需审核） */
    public static final String MSG_TASK_PROGRESS_REPORT = "TASK_PROGRESS_REPORT";

    /** 负责人/管理者通过进度接口最多上报到此值；100% 仅随任务「已完成」落库。 */
    private static final int PROGRESS_REPORT_MAX = 89;

    private final TaskMapper taskMapper;
    private final TaskDependencyMapper taskDependencyMapper;
    private final UserMapper userMapper;
    private final TeamService teamService;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final InAppMessageService inAppMessageService;

    public Task requireTask(Long id) {
        Task t = taskMapper.findById(id);
        if (t == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return t;
    }

    public void assertPredecessorsComplete(Long taskId) {
        List<TaskDependency> deps = taskDependencyMapper.findBySuccessorId(taskId);
        for (TaskDependency d : deps) {
            Task p = taskMapper.findById(d.getPredecessorId());
            if (p == null || (!"COMPLETED".equals(p.getStatus()) && !"ARCHIVED".equals(p.getStatus()))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "前置任务未完成，无法开始当前任务");
            }
        }
    }

    @Transactional
    public Task create(Long teamId, Task task, List<Long> predecessorIds, UserPrincipal actor) {
        var team = teamService.requireTeam(teamId);
        teamService.assertManager(actor, team);
        task.setTeamId(teamId);
        task.setCreatorId(actor.getId());
        if (task.getStatus() == null) {
            task.setStatus("CREATED");
        }
        if (task.getDifficulty() == null) {
            task.setDifficulty(BigDecimal.valueOf(1));
        }
        if (task.getPriority() == null) {
            task.setPriority(3);
        }
        if (task.getProgress() == null) {
            task.setProgress(0);
        }
        if (task.getAssigneeId() != null) {
            if (!teamService.isApprovedMember(teamId, task.getAssigneeId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "被指派人须为已通过审核的成员");
            }
            User assignee = userMapper.findById(task.getAssigneeId());
            if (assignee == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "用户不存在");
            }
        }
        task.setRiskLevel("GREEN");
        task.setDelayProbability(BigDecimal.ZERO);
        taskMapper.insert(task);
        Long sid = task.getId();
        if (predecessorIds != null) {
            for (Long pid : predecessorIds) {
                if (pid == null || pid.equals(sid)) {
                    continue;
                }
                Task pred = taskMapper.findById(pid);
                if (pred == null || !pred.getTeamId().equals(teamId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "前置任务无效");
                }
                TaskDependency dep = new TaskDependency();
                dep.setPredecessorId(pid);
                dep.setSuccessorId(sid);
                taskDependencyMapper.insert(dep);
            }
        }
        auditService.log(actor.getId(), "TASK_CREATE", "Task", sid, task.getTitle());
        recomputeRisk(sid);
        return taskMapper.findById(sid);
    }

    @Transactional
    public Task update(Long taskId, Task patch, UserPrincipal actor) {
        Task existing = requireTask(taskId);
        var team = teamService.requireTeam(existing.getTeamId());
        teamService.assertManager(actor, team);
        if ("IN_PROGRESS".equals(patch.getStatus()) && !"IN_PROGRESS".equals(existing.getStatus())) {
            assertPredecessorsComplete(taskId);
        }
        existing.setTitle(patch.getTitle());
        existing.setDescription(patch.getDescription());
        existing.setDifficulty(patch.getDifficulty() != null ? patch.getDifficulty() : existing.getDifficulty());
        existing.setPriority(patch.getPriority() != null ? patch.getPriority() : existing.getPriority());
        existing.setEstHours(patch.getEstHours());
        existing.setDeadline(patch.getDeadline());
        existing.setRequiredSkillsJson(patch.getRequiredSkillsJson());
        if (patch.getStatus() != null) {
            existing.setStatus(patch.getStatus());
            if ("COMPLETED".equals(patch.getStatus())) {
                existing.setProgress(100);
            }
        }
        if (patch.getAssigneeId() != null) {
            existing.setAssigneeId(patch.getAssigneeId());
        }
        int n = taskMapper.update(existing);
        if (n == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已被他人更新，请刷新后重试");
        }
        auditService.log(actor.getId(), "TASK_UPDATE", "Task", taskId, null);
        recomputeRisk(taskId);
        return taskMapper.findById(taskId);
    }

    public void assertCanAccessTeamTasks(Long teamId, UserPrincipal actor) {
        teamService.requireTeam(teamId);
        if ("ADMIN".equals(actor.getRole())) {
            return;
        }
        if ("MANAGER".equals(actor.getRole())) {
            var team = teamService.requireTeam(teamId);
            teamService.assertManager(actor, team);
            return;
        }
        teamService.assertMemberApproved(actor, teamId);
    }

    public List<Task> listTeamTasks(Long teamId, UserPrincipal actor) {
        assertCanAccessTeamTasks(teamId, actor);
        return taskMapper.findByTeamId(teamId);
    }

    public Task getTaskForViewer(Long taskId, UserPrincipal actor) {
        Task t = requireTask(taskId);
        assertCanAccessTeamTasks(t.getTeamId(), actor);
        return t;
    }

    public List<Task> listPredecessorTasks(Long taskId, UserPrincipal actor) {
        Task t = requireTask(taskId);
        assertCanAccessTeamTasks(t.getTeamId(), actor);
        List<TaskDependency> deps = taskDependencyMapper.findBySuccessorId(taskId);
        List<Task> out = new ArrayList<>();
        for (TaskDependency d : deps) {
            Task pred = taskMapper.findById(d.getPredecessorId());
            if (pred != null) {
                out.add(pred);
            }
        }
        return out;
    }

    public List<Task> myTasks(UserPrincipal actor) {
        return taskMapper.findByAssigneeId(actor.getId());
    }

    @Transactional
    public Task updateProgress(Long taskId, int progress, int version, UserPrincipal actor) {
        Task t = requireTask(taskId);
        boolean isAssignee = t.getAssigneeId() != null && t.getAssigneeId().equals(actor.getId());
        if (!isAssignee) {
            if (!"ADMIN".equals(actor.getRole()) && !"MANAGER".equals(actor.getRole())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "只能更新自己的任务进度");
            }
        }
        if ("MANAGER".equals(actor.getRole())) {
            var team = teamService.requireTeam(t.getTeamId());
            teamService.assertManager(actor, team);
        }
        if ("COMPLETED".equals(t.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "已完成任务不可修改进度");
        }
        if (progress < 0 || progress > PROGRESS_REPORT_MAX) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "进度仅可上报 0%–" + PROGRESS_REPORT_MAX + "%；任务审批通过为「已完成」后将自动更新为 100%");
        }
        if (isAssignee && !"IN_PROGRESS".equals(t.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "仅在进行中时可由负责人上报进度");
        }
        int oldProgress = t.getProgress() != null ? t.getProgress() : 0;
        int n = taskMapper.updateProgress(taskId, progress, version);
        if (n == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已被他人更新，请刷新后重试");
        }
        if (isAssignee && progress != oldProgress) {
            notifyManagerProgressReport(t, oldProgress, progress, actor);
        }
        recomputeRisk(taskId);
        return taskMapper.findById(taskId);
    }

    /**
     * 审批通过后的状态变更（内部调用）；若为 COMPLETED 会同步将进度置为 100。
     */
    @Transactional
    public void applyApprovedStatusChange(Long taskId, String newStatus, Long reviewerUserId) {
        Task task = requireTask(taskId);
        String to = newStatus != null ? newStatus.trim() : "";
        if (to.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "目标状态无效");
        }
        if ("IN_PROGRESS".equals(to) && !"IN_PROGRESS".equals(task.getStatus())) {
            assertPredecessorsComplete(taskId);
        }
        if (task.getVersion() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "任务版本异常，请重试审批");
        }
        int n = taskMapper.updateStatusApplyApproved(taskId, to, task.getVersion());
        if (n == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已被他人更新，请重试审批");
        }
        recomputeRisk(taskId);
        auditService.log(reviewerUserId, "TASK_STATUS_APPLY", "Task", taskId, to);
    }

    @Transactional
    public Task reassign(Long taskId, Long assigneeId, int version, UserPrincipal actor) {
        Task t = requireTask(taskId);
        var team = teamService.requireTeam(t.getTeamId());
        teamService.assertManager(actor, team);
        if (assigneeId != null) {
            if (!teamService.isApprovedMember(t.getTeamId(), assigneeId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "被指派人须为已通过审核的成员");
            }
            User u = userMapper.findById(assigneeId);
            if (u == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "用户不存在");
            }
        }
        int n = taskMapper.updateAssignee(taskId, assigneeId, version);
        if (n == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已被他人更新，请刷新后重试");
        }
        auditService.log(actor.getId(), "TASK_REASSIGN", "Task", taskId, String.valueOf(assigneeId));
        recomputeRisk(taskId);
        return taskMapper.findById(taskId);
    }

    @Transactional
    public void replaceDependencies(Long taskId, List<Long> predecessorIds, UserPrincipal actor) {
        Task t = requireTask(taskId);
        var team = teamService.requireTeam(t.getTeamId());
        teamService.assertManager(actor, team);
        taskDependencyMapper.deleteBySuccessor(taskId);
        if (predecessorIds != null) {
            for (Long pid : predecessorIds) {
                Task pred = taskMapper.findById(pid);
                if (pred == null || !pred.getTeamId().equals(t.getTeamId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "前置任务无效");
                }
                TaskDependency dep = new TaskDependency();
                dep.setPredecessorId(pid);
                dep.setSuccessorId(taskId);
                taskDependencyMapper.insert(dep);
            }
        }
    }

    public void recomputeRisk(Long taskId) {
        Task t = requireTask(taskId);
        String previousLevel = normalizeRiskLevel(t.getRiskLevel());
        if (!List.of("CREATED", "IN_PROGRESS", "SUSPENDED").contains(t.getStatus())) {
            taskMapper.updateRisk(taskId, "GREEN", BigDecimal.ZERO);
            return;
        }
        User assignee = t.getAssigneeId() != null ? userMapper.findById(t.getAssigneeId()) : null;
        double delayHist = assignee != null && assignee.getDelayHistoryScore() != null
                ? assignee.getDelayHistoryScore().doubleValue() : 0;
        LocalDate start = t.getCreatedAt() != null
                ? t.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();
        double diff = t.getDifficulty() != null ? t.getDifficulty().doubleValue() : 1;
        BigDecimal p = RiskModel.delayProbability(
                appProperties.getRisk(),
                t.getProgress() != null ? t.getProgress() : 0,
                start,
                t.getDeadline(),
                diff,
                delayHist
        );
        String level = RiskModel.level(appProperties.getRisk(), p);
        taskMapper.updateRisk(taskId, level, p);
        if (riskEscalatedAcrossThreshold(previousLevel, level)) {
            notifyManagerRiskEscalation(t, previousLevel, level, p);
        }
    }

    private static String normalizeRiskLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "GREEN";
        }
        return raw.trim().toUpperCase();
    }

    /**
     * 相对原等级上升，且新等级至少达到橙区（达到或超过配置的橙/红阈值）。
     */
    private static boolean riskEscalatedAcrossThreshold(String previousLevel, String newLevel) {
        int prev = riskRank(previousLevel);
        int next = riskRank(newLevel);
        return next > prev && next >= 1;
    }

    private static int riskRank(String level) {
        String l = normalizeRiskLevel(level);
        if ("RED".equals(l)) {
            return 2;
        }
        if ("ORANGE".equals(l)) {
            return 1;
        }
        return 0;
    }

    private void notifyManagerProgressReport(Task task, int fromPct, int toPct, UserPrincipal actor) {
        try {
            Team team = teamService.requireTeam(task.getTeamId());
            Long managerId = team.getManagerId();
            if (managerId == null || managerId.equals(actor.getId())) {
                return;
            }
            User reporter = userMapper.findById(actor.getId());
            String reporterLabel = reporter != null && reporter.getDisplayName() != null && !reporter.getDisplayName().isBlank()
                    ? reporter.getDisplayName()
                    : (reporter != null && reporter.getUsername() != null ? reporter.getUsername() : ("用户#" + actor.getId()));
            String teamName = team.getName() != null ? team.getName() : ("团队#" + team.getId());
            String title = "【进度上报】" + (task.getTitle() != null ? task.getTitle() : ("任务#" + task.getId()));
            String body = String.format(
                    "团队「%s」中，负责人「%s」将任务「%s」的进度由 %d%% 更新为 %d%%。（无需审核）",
                    teamName,
                    reporterLabel,
                    task.getTitle() != null ? task.getTitle() : ("#" + task.getId()),
                    fromPct,
                    toPct
            );
            inAppMessageService.send(managerId, title, body, MSG_TASK_PROGRESS_REPORT, "Task", task.getId());
        } catch (Exception e) {
            log.warn("进度上报消息发送失败 taskId={}: {}", task.getId(), e.getMessage());
        }
    }

    private void notifyManagerRiskEscalation(Task task, String oldLevel, String newLevel, BigDecimal probability) {
        try {
            Team team = teamService.requireTeam(task.getTeamId());
            Long managerId = team.getManagerId();
            if (managerId == null) {
                return;
            }
            double pct = probability != null ? probability.doubleValue() * 100.0 : 0;
            String pctStr = String.format(java.util.Locale.ROOT, "%.1f", pct);
            String teamName = team.getName() != null ? team.getName() : ("团队#" + team.getId());
            String title = "【风险预警】任务延期风险上升";
            String body = String.format(
                    "团队「%s」中的任务「%s」风险等级已由 %s 上升至 %s，模型评估延期概率约 %s%%。"
                            + "（阈值：≥%.0f%% 为橙区，≥%.0f%% 为红区）请及时关注进度与截止日期。",
                    teamName,
                    task.getTitle() != null ? task.getTitle() : ("#" + task.getId()),
                    riskLevelCn(oldLevel),
                    riskLevelCn(newLevel),
                    pctStr,
                    appProperties.getRisk().getOrangeThreshold() * 100,
                    appProperties.getRisk().getRedThreshold() * 100
            );
            inAppMessageService.send(managerId, title, body, MSG_TASK_RISK_ESCALATION, "Task", task.getId());
        } catch (Exception e) {
            log.warn("风险预警消息发送失败 taskId={}: {}", task.getId(), e.getMessage());
        }
    }

    private static String riskLevelCn(String level) {
        return switch (normalizeRiskLevel(level)) {
            case "RED" -> "红区（高）";
            case "ORANGE" -> "橙区（中）";
            default -> "绿区（低）";
        };
    }

    public void recomputeTeamRisks(Long teamId, UserPrincipal actor) {
        var team = teamService.requireTeam(teamId);
        teamService.assertManager(actor, team);
        for (Task t : taskMapper.findInProgressOrCreatedByTeam(teamId)) {
            recomputeRisk(t.getId());
        }
    }

    public List<Task> risksForTeam(Long teamId, UserPrincipal actor) {
        teamService.requireTeam(teamId);
        if ("MANAGER".equals(actor.getRole())) {
            teamService.assertManager(actor, teamService.requireTeam(teamId));
        } else if (!"ADMIN".equals(actor.getRole())) {
            teamService.assertMemberApproved(actor, teamId);
        }
        return taskMapper.findInProgressOrCreatedByTeam(teamId);
    }
}
