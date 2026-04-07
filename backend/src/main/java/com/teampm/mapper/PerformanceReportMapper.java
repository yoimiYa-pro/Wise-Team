package com.teampm.mapper;

import com.teampm.domain.PerformanceReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PerformanceReportMapper {
    List<PerformanceReport> findByCycleId(@Param("cycleId") Long cycleId);

    List<PerformanceReport> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    int upsert(PerformanceReport r);
}
