package com.teampm.mapper;

import com.teampm.domain.PerformanceCycle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PerformanceCycleMapper {
    PerformanceCycle findById(@Param("id") Long id);

    List<PerformanceCycle> findByTeamId(@Param("teamId") Long teamId);

    int insert(PerformanceCycle c);

    int closeCycle(@Param("id") Long id);
}
