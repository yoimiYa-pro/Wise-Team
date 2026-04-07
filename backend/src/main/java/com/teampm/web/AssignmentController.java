package com.teampm.web;

import com.teampm.security.SecurityUtils;
import com.teampm.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams/{teamId}/assign")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping("/recommend")
    public List<Map<String, Object>> recommend(@PathVariable Long teamId,
                                                @RequestParam(required = false) String requiredSkillsJson) {
        return assignmentService.recommend(teamId, requiredSkillsJson, SecurityUtils.requireUser());
    }
}
