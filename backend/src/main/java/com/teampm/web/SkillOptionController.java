package com.teampm.web;

import com.teampm.domain.SkillOption;
import com.teampm.security.SecurityUtils;
import com.teampm.service.SkillOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skill-options")
@RequiredArgsConstructor
public class SkillOptionController {

    private final SkillOptionService skillOptionService;

    @GetMapping("/global")
    public List<SkillOption> global() {
        SecurityUtils.requireUser();
        return skillOptionService.listGlobal();
    }

    @GetMapping("/catalog")
    public List<SkillOptionService.CatalogItem> catalog() {
        SecurityUtils.requireUser();
        return skillOptionService.mergedCatalog();
    }
}
