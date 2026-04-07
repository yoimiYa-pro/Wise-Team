package com.teampm.web;

import com.teampm.domain.UserProfileRequest;
import com.teampm.dto.ProfileRequestView;
import com.teampm.exception.ApiException;
import com.teampm.security.SecurityUtils;
import com.teampm.security.UserPrincipal;
import com.teampm.service.UserProfileRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profile-requests")
@RequiredArgsConstructor
public class UserProfileRequestController {

    private final UserProfileRequestService userProfileRequestService;

    @PostMapping("/submit")
    public UserProfileRequest submit(@RequestBody UserProfileRequestService.SubmitBody body,
            @AuthenticationPrincipal UserPrincipal p) {
        return userProfileRequestService.submit(body, SecurityUtils.requireUser());
    }

    @GetMapping("/me/pending")
    public UserProfileRequest myPending(@AuthenticationPrincipal UserPrincipal p) {
        return userProfileRequestService.myPending(SecurityUtils.requireUser());
    }

    @GetMapping("/pending")
    public List<ProfileRequestView> pending(@AuthenticationPrincipal UserPrincipal p) {
        return userProfileRequestService.listPendingAdmin(SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/approve")
    public void approve(@PathVariable Long id, @RequestBody(required = false) UserProfileRequestService.ReviewBody body,
            @AuthenticationPrincipal UserPrincipal p) {
        String c = body != null ? body.getReviewComment() : null;
        userProfileRequestService.approve(id, c, SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable Long id, @RequestBody UserProfileRequestService.ReviewBody body,
            @AuthenticationPrincipal UserPrincipal p) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请提交驳回原因");
        }
        userProfileRequestService.reject(id, body.getReviewComment(), SecurityUtils.requireUser());
    }
}
