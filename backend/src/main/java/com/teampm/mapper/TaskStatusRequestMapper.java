package com.teampm.mapper;

import com.teampm.domain.TaskStatusRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskStatusRequestMapper {

    TaskStatusRequest findById(@Param("id") Long id);

    TaskStatusRequest findPendingByTaskId(@Param("taskId") Long taskId);

    List<TaskStatusRequest> findPendingForAdmin();

    List<TaskStatusRequest> findPendingForTeamIds(@Param("teamIds") List<Long> teamIds);

    int insert(TaskStatusRequest row);

    int updateReview(TaskStatusRequest row);
}
