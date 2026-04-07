package com.teampm.mapper;

import com.teampm.domain.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface TaskMapper {
    Task findById(@Param("id") Long id);

    List<Task> findByTeamId(@Param("teamId") Long teamId);

    List<Task> findByAssigneeId(@Param("assigneeId") Long assigneeId);

    List<Task> findActiveByTeam(@Param("teamId") Long teamId);

    int insert(Task task);

    int update(Task task);

    /**
     * 审批通过变更状态：若为 COMPLETED 则在库内将 progress 置为 100，避免仅改 status 时进度未落库。
     */
    int updateStatusApplyApproved(@Param("id") Long id, @Param("newStatus") String newStatus, @Param("version") Integer version);

    int updateProgress(@Param("id") Long id, @Param("progress") Integer progress, @Param("version") Integer version);

    int updateAssignee(@Param("id") Long id, @Param("assigneeId") Long assigneeId, @Param("version") Integer version);

    int updateRisk(@Param("id") Long id, @Param("riskLevel") String riskLevel,
                   @Param("delayProbability") java.math.BigDecimal delayProbability);

    BigDecimal sumRemainingHoursByAssignee(@Param("assigneeId") Long assigneeId);

    List<Task> findInProgressOrCreatedByTeam(@Param("teamId") Long teamId);
}
