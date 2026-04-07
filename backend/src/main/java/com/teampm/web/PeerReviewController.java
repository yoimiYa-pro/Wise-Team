package com.teampm.web;

import com.teampm.security.SecurityUtils;
import com.teampm.service.PerformanceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/peer-reviews")
@RequiredArgsConstructor
public class PeerReviewController {

    private final PerformanceService performanceService;

    @PostMapping("/teams/{teamId}/cycles/{cycleId}")
    public void submit(@PathVariable Long teamId, @PathVariable Long cycleId, @RequestBody PeerReq req) {
        performanceService.submitPeerReview(teamId, cycleId, req.getTargetUserId(), req.getDimensions(),
                SecurityUtils.requireUser());
    }

    @Data
    public static class PeerReq {
        private Long targetUserId;
        private Map<String, Double> dimensions;
    }
}
