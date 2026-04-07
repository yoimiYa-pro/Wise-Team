package com.teampm.web;

import com.teampm.security.SecurityUtils;
import com.teampm.service.AuditService;
import com.teampm.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/system")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSystemController {

    private final SystemConfigService systemConfigService;
    private final AuditService auditService;

    @GetMapping("/config")
    public Map<String, String> getConfig() {
        return systemConfigService.allEffective();
    }

    @PutMapping("/config")
    public void putConfig(@RequestBody Map<String, String> body) {
        Long actor = SecurityUtils.requireUser().getId();
        for (Map.Entry<String, String> e : body.entrySet()) {
            systemConfigService.put(e.getKey(), e.getValue());
        }
        auditService.log(actor, "SYSTEM_CONFIG", "SystemConfig", null, body.toString());
    }
}
