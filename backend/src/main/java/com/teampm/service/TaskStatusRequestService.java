package com.teampm.service;

import com.teampm.domain.Task;
import com.teampm.domain.TaskStatusRequest;
import com.teampm.domain.Team;
import com.teampm.domain.User;
import com.teampm.dto.TaskStatusRequestView;
import com.teampm.exception.ApiException;
import com.teampm.mapper.TaskMapper;
import com.teampm.mapper.TaskStatusRequestMapper;
import com.teampm.mapper.TeamMapper;
import com.teampm.mapper.UserMapper;
import com.teampm.security.UserPrincipal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskStatusRequestService {

    public static final String REF_TYPE = "TASK_STATUS_REQUEST";
    public static final String MSG_APPLY = "TASK_STATUS_APPLY";
    public static final String MSG_APPROVED = "TASK_STATUS_APPROVED";
    public static final String MSG_REJECTED = "TASK_STATUS_REJECTED";

    private final TaskStatusRequestMapper requestMapper;
    private final TaskMapper taskMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final TeamService teamService;
    private final TaskService taskService;
    private final InAppMessageService messageService;

    @Transactional
    public TaskStatusRequest submit(Long taskId, String toStatus, String applyReason, UserPrincipal actor) {
        Task task = taskService.requireTask(taskId);
        if (task.getAssigneeId() == null || !task.getAssigneeId().equals(actor.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "仅任务负责人可提交状态变更申请");
        }
        teamService.assertMemberApproved(actor, task.getTeamId());
        String from = task.getStatus();
        if ("COMPLETED".equals(from) || "ARCHIVED".equals(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "任务已结束，无法申请");
        }
        if (!allowedTransition(from, toStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不允许的状态流转：" + from + " → " + toStatus);
        }
        if ("IN_PROGRESS".equals(toStatus) && !"IN_PROGRESS".equals(from)) {
            taskService.assertPredecessorsComplete(taskId);
        }
        if (requestMapper.findPendingByTaskId(taskId) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "该任务已有待审批申请");
        }
        TaskStatusRequest r = new TaskStatusRequest();
        r.setTaskId(taskId);
        r.setApplicantId(actor.getId());
        r.setFromStatus(from);
        r.setToStatus(toStatus);
        r.setApplyReason(trimOrNull(applyReason));
        r.setReviewStatus("PENDING");
        requestMapper.insert(r);
        Long rid = r.getId();

        User applicant = userMapper.findById(actor.getId());
        String an = applicant != null ? applicant.getUsername() : "?";
        String title = "【待审批】任务状态变更";
        String body = String.format(
                "任务「%s」负责人 %s 申请：%s → %s。%s",
                task.getTitle(),
                an,
                from,
                toStatus,
                r.getApplyReason() != null ? "说明：" + r.getApplyReason() : ""
        );
        messageService.sendToMany(collectReviewerUserIds(task.getTeamId()), title, body, MSG_APPLY, REF_TYPE, rid);
        return requestMapper.findById(rid);
    }

    public List<TaskStatusRequestView> listPending(UserPrincipal actor) {
        List<TaskStatusRequest> list;
        if ("ADMIN".equals(actor.getRole())) {
            list = requestMapper.findPendingForAdmin();
        } else if ("MANAGER".equals(actor.getRole())) {
            List<Team> teams = teamMapper.findByManagerId(actor.getId());
            List<Long> tids = teams.stream().map(Team::getId).toList();
            if (tids.isEmpty()) {
                list = List.of();
            } else {
                list = requestMapper.findPendingForTeamIds(tids);
            }
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权查看待审批");
        }
        return enrich(list);
    }

    @Transactional
    public void approve(Long requestId, String reviewComment, UserPrincipal actor) {
        TaskStatusRequest r = requirePending(requestId);
        assertCanReview(r, actor);
        Task task = taskService.requireTask(r.getTaskId());
        taskService.applyApprovedStatusChange(r.getTaskId(), r.getToStatus(), actor.getId());
        r.setReviewStatus("APPROVED");
        r.setReviewerId(actor.getId());
        r.setReviewComment(trimOrNull(reviewComment));
        requestMapper.updateReview(r);
        User rev = userMapper.findById(actor.getId());
        String revName = rev != null ? rev.getUsername() : "审核人";
        String title = "【已通过】任务状态变更";
        String body = String.format(
                "您申请将任务「%s」从 %s 调整为 %s，已由 %s 批准。%s",
                task.getTitle(),
                r.getFromStatus(),
                r.getToStatus(),
                revName,
                r.getReviewComment() != null ? "备注：" + r.getReviewComment() : ""
        );
        messageService.send(r.getApplicantId(), title, body, MSG_APPROVED, REF_TYPE, requestId);
    }

    @Transactional
    public void reject(Long requestId, String reviewComment, UserPrincipal actor) {
        if (reviewComment == null || reviewComment.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "驳回须填写原因");
        }
        TaskStatusRequest r = requirePending(requestId);
        assertCanReview(r, actor);
        Task task = taskService.requireTask(r.getTaskId());
        r.setReviewStatus("REJECTED");
        r.setReviewerId(actor.getId());
        r.setReviewComment(reviewComment.trim());
        requestMapper.updateReview(r);
        User rev = userMapper.findById(actor.getId());
        String revName = rev != null ? rev.getUsername() : "审核人";
        String title = "【已驳回】任务状态变更";
        String body = String.format(
                "您申请将任务「%s」从 %s 调整为 %s，已被 %s 驳回。\n驳回原因：%s\n请按意见整改后可重新提交申请。",
                task.getTitle(),
                r.getFromStatus(),
                r.getToStatus(),
                revName,
                r.getReviewComment()
        );
        messageService.send(r.getApplicantId(), title, body, MSG_REJECTED, REF_TYPE, requestId);
    }

    private TaskStatusRequest requirePending(Long id) {
        TaskStatusRequest r = requestMapper.findById(id);
        if (r == null || !"PENDING".equals(r.getReviewStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "申请不存在或已处理");
        }
        return r;
    }

    private void assertCanReview(TaskStatusRequest r, UserPrincipal actor) {
        Task task = taskService.requireTask(r.getTaskId());
        Team team = teamService.requireTeam(task.getTeamId());
        if ("ADMIN".equals(actor.getRole())) {
            return;
        }
        if ("MANAGER".equals(actor.getRole()) && team.getManagerId().equals(actor.getId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "仅团队管理者或系统管理员可审批");
    }

    private List<Long> collectReviewerUserIds(Long teamId) {
        Team team = teamMapper.findById(teamId);
        Set<Long> ids = new LinkedHashSet<>();
        if (team != null && team.getManagerId() != null) {
            ids.add(team.getManagerId());
        }
        for (User u : userMapper.findAll()) {
            if ("ADMIN".equals(u.getRole()) && u.getStatus() != null && u.getStatus() != 0) {
                ids.add(u.getId());
            }
        }
        return new ArrayList<>(ids);
    }

    private List<TaskStatusRequestView> enrich(List<TaskStatusRequest> list) {
        List<TaskStatusRequestView> out = new ArrayList<>(list.size());
        for (TaskStatusRequest r : list) {
            TaskStatusRequestView v = new TaskStatusRequestView();
            v.setRequest(r);
            Task t = taskMapper.findById(r.getTaskId());
            if (t != null) {
                v.setTaskTitle(t.getTitle());
                v.setTeamId(t.getTeamId());
                Team tm = teamMapper.findById(t.getTeamId());
                v.setTeamName(tm != null ? tm.getName() : "-");
            }
            User u = userMapper.findById(r.getApplicantId());
            v.setApplicantUsername(u != null ? u.getUsername() : "-");
            out.add(v);
        }
        return out;
    }

    private static boolean allowedTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case "CREATED" -> "IN_PROGRESS".equals(to) || "SUSPENDED".equals(to);
            case "IN_PROGRESS" -> "SUSPENDED".equals(to) || "COMPLETED".equals(to);
            case "SUSPENDED" -> "IN_PROGRESS".equals(to) || "COMPLETED".equals(to);
            default -> false;
        };
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Data
    public static class SubmitBody {
        private String toStatus;
        private String applyReason;
    }

    @Data
    public static class ReviewBody {
        private String reviewComment;
    }
}
