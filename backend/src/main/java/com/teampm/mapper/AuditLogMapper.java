package com.teampm.mapper;

import com.teampm.domain.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditLogMapper {
    int insert(AuditLog log);

    List<AuditLog> findRecent(@Param("limit") int limit);
}
