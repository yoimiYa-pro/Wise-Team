package com.teampm.web;

import com.teampm.domain.GlobalFceAhp;
import com.teampm.security.SecurityUtils;
import com.teampm.service.AuditService;
import com.teampm.service.GlobalFceAhpService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/system/fce-ahp")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminGlobalFceAhpController {

    private final GlobalFceAhpService globalFceAhpService;
    private final AuditService auditService;

    @GetMapping
    public GlobalFceAhp get() {
        return globalFceAhpService.getSingleton();
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody MatrixReq req) {
        SecurityUtils.requireUser();
        return globalFceAhpService.preview(req.getMatrix());
    }

    @PutMapping
    public GlobalFceAhp save(@RequestBody MatrixReq req) {
        Long actorId = SecurityUtils.requireUser().getId();
        GlobalFceAhp saved = globalFceAhpService.saveMatrix(req.getMatrix());
        auditService.log(actorId, "GLOBAL_FCE_AHP_SAVE", "GlobalFceAhp", GlobalFceAhp.SINGLETON_ID, null);
        return saved;
    }

    @Data
    public static class MatrixReq {
        private double[][] matrix;
    }
}
