package com.teampm.web;

import com.teampm.domain.TeamAhp;
import com.teampm.security.SecurityUtils;
import com.teampm.service.AhpService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/teams/{teamId}/ahp")
@RequiredArgsConstructor
public class AhpController {

    private final AhpService ahpService;

    @GetMapping
    public TeamAhp get(@PathVariable Long teamId) {
        return ahpService.getForTeam(teamId, SecurityUtils.requireUser());
    }

    @PostMapping
    public TeamAhp save(@PathVariable Long teamId, @RequestBody MatrixReq req) {
        return ahpService.saveMatrix(teamId, req.getMatrix(), SecurityUtils.requireUser());
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody MatrixReq req) {
        SecurityUtils.requireUser();
        return ahpService.preview(req.getMatrix());
    }

    @Data
    public static class MatrixReq {
        private double[][] matrix;
    }
}
