package com.teampm.service;

import com.teampm.domain.AuditLog;
import com.teampm.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 关键操作写审计表，供管理端追溯（actor、动作、资源类型与 ID、详情 JSON/文本）。
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public void log(Long actorId, String action, String resourceType, Long resourceId, String detail) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDetail(detail);
        auditLogMapper.insert(log);
    }
}
