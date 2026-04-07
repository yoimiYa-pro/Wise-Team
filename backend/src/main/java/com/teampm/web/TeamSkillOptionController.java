package com.teampm.web;

import com.teampm.domain.SkillOption;
import com.teampm.security.SecurityUtils;
import com.teampm.service.SkillOptionService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teams/{teamId}/skill-options")
@RequiredArgsConstructor
public class TeamSkillOptionController {

    private final SkillOptionService skillOptionService;

    @GetMapping
    public List<SkillOption> list(@PathVariable Long teamId) {
        SecurityUtils.requireUser();
        return skillOptionService.listForTeam(teamId);
    }

    @PostMapping
    public SkillOption create(@PathVariable Long teamId, @RequestBody SkillOptionBody body) {
        return skillOptionService.createTeam(teamId, body.getSkillCode(), body.getLabel(), body.getSortOrder(), SecurityUtils.requireUser());
    }

    @PutMapping("/{id}")
    public SkillOption update(@PathVariable Long teamId, @PathVariable Long id, @RequestBody SkillOptionBody body) {
        return skillOptionService.updateTeam(teamId, id, body.getSkillCode(), body.getLabel(), body.getSortOrder(), SecurityUtils.requireUser());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long teamId, @PathVariable Long id) {
        skillOptionService.deleteTeam(teamId, id, SecurityUtils.requireUser());
    }

    @Data
    public static class SkillOptionBody {
        @NotBlank
        private String skillCode;
        @NotBlank
        private String label;
        private Integer sortOrder;
    }
}
