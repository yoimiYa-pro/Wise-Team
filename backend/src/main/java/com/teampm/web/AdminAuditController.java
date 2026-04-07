package com.teampm.web;

import com.teampm.domain.AuditLog;
import com.teampm.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditLogMapper auditLogMapper;

    @GetMapping
    public List<AuditLog> list(@RequestParam(defaultValue = "200") int limit) {
        return auditLogMapper.findRecent(Math.min(limit, 500));
    }
}
