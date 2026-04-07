package com.teampm.web;

import com.teampm.domain.Task;
import com.teampm.domain.TaskStatusRequest;
import com.teampm.security.SecurityUtils;
import com.teampm.security.UserPrincipal;
import com.teampm.service.TaskService;
import com.teampm.service.TaskStatusRequestService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskStatusRequestService taskStatusRequestService;

    @PostMapping("/teams/{teamId}")
    public Task create(@PathVariable Long teamId, @RequestBody CreateTaskReq req, @AuthenticationPrincipal UserPrincipal p) {
        return taskService.create(teamId, req.getTask(), req.getPredecessorIds(), SecurityUtils.requireUser());
    }

    @PutMapping("/{id}")
    public Task update(@PathVariable Long id, @RequestBody Task patch, @AuthenticationPrincipal UserPrincipal p) {
        return taskService.update(id, patch, SecurityUtils.requireUser());
    }

    @GetMapping("/teams/{teamId}")
    public List<Task> byTeam(@PathVariable Long teamId, @AuthenticationPrincipal UserPrincipal p) {
        return taskService.listTeamTasks(teamId, SecurityUtils.requireUser());
    }

    @GetMapping("/me")
    public List<Task> mine(@AuthenticationPrincipal UserPrincipal p) {
        return taskService.myTasks(SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/status-requests")
    public TaskStatusRequest submitStatusRequest(@PathVariable Long id, @RequestBody TaskStatusRequestService.SubmitBody body) {
        return taskStatusRequestService.submit(id, body.getToStatus(), body.getApplyReason(), SecurityUtils.requireUser());
    }

    @PatchMapping("/{id}/progress")
    public Task progress(@PathVariable Long id, @RequestBody ProgressReq req, @AuthenticationPrincipal UserPrincipal p) {
        return taskService.updateProgress(id, req.getProgress(), req.getVersion(), SecurityUtils.requireUser());
    }

    @PatchMapping("/{id}/reassign")
    public Task reassign(@PathVariable Long id, @RequestBody ReassignReq req, @AuthenticationPrincipal UserPrincipal p) {
        return taskService.reassign(id, req.getAssigneeId(), req.getVersion(), SecurityUtils.requireUser());
    }

    @PutMapping("/{id}/dependencies")
    public void deps(@PathVariable Long id, @RequestBody DepsReq req, @AuthenticationPrincipal UserPrincipal p) {
        taskService.replaceDependencies(id, req.getPredecessorIds(), SecurityUtils.requireUser());
    }

    @PostMapping("/teams/{teamId}/risks/refresh")
    public void refreshRisks(@PathVariable Long teamId, @AuthenticationPrincipal UserPrincipal p) {
        taskService.recomputeTeamRisks(teamId, SecurityUtils.requireUser());
    }

    @GetMapping("/{id}")
    public Task one(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        return taskService.getTaskForViewer(id, SecurityUtils.requireUser());
    }

    @GetMapping("/{id}/predecessors")
    public List<Task> predecessors(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        return taskService.listPredecessorTasks(id, SecurityUtils.requireUser());
    }

    @Data
    public static class CreateTaskReq {
        private Task task;
        private List<Long> predecessorIds;
    }

    @Data
    public static class ProgressReq {
        private int progress;
        private int version;
    }

    @Data
    public static class ReassignReq {
        private Long assigneeId;
        private int version;
    }

    @Data
    public static class DepsReq {
        private List<Long> predecessorIds;
    }
}
