package com.teampm.mapper;

import com.teampm.domain.WorkloadWeekly;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkloadWeeklyMapper {
    WorkloadWeekly findByUserAndWeek(@Param("userId") Long userId, @Param("yearWeek") String yearWeek);

    List<WorkloadWeekly> findByUserIdOrderByWeek(@Param("userId") Long userId, @Param("limit") int limit);

    int upsert(WorkloadWeekly row);
}
