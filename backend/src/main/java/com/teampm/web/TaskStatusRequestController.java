package com.teampm.web;

import com.teampm.dto.TaskStatusRequestView;
import com.teampm.security.SecurityUtils;
import com.teampm.service.TaskStatusRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/task-status-requests")
@RequiredArgsConstructor
public class TaskStatusRequestController {

    private final TaskStatusRequestService taskStatusRequestService;

    @GetMapping("/pending")
    public List<TaskStatusRequestView> pending() {
        return taskStatusRequestService.listPending(SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/approve")
    public void approve(@PathVariable Long id, @RequestBody(required = false) TaskStatusRequestService.ReviewBody body) {
        taskStatusRequestService.approve(id, body != null ? body.getReviewComment() : null, SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable Long id, @RequestBody TaskStatusRequestService.ReviewBody body) {
        taskStatusRequestService.reject(id, body != null ? body.getReviewComment() : "", SecurityUtils.requireUser());
    }
}
