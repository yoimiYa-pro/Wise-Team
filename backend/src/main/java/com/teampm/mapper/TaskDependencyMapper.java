package com.teampm.mapper;

import com.teampm.domain.TaskDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskDependencyMapper {
    List<TaskDependency> findBySuccessorId(@Param("successorId") Long successorId);

    List<TaskDependency> findByPredecessorId(@Param("predecessorId") Long predecessorId);

    int insert(TaskDependency d);

    int deleteBySuccessor(@Param("successorId") Long successorId);
}
