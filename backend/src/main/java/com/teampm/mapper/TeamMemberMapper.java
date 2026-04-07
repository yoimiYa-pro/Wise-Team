package com.teampm.mapper;

import com.teampm.domain.TeamMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeamMemberMapper {
    TeamMember findById(@Param("id") Long id);

    TeamMember findByTeamAndUser(@Param("teamId") Long teamId, @Param("userId") Long userId);

    List<TeamMember> findByTeamId(@Param("teamId") Long teamId);

    List<TeamMember> findByUserId(@Param("userId") Long userId);

    int insert(TeamMember tm);

    int updateApproval(@Param("id") Long id, @Param("approvalStatus") String approvalStatus);

    int deleteById(@Param("id") Long id);

    List<Long> findApprovedUserIds(@Param("teamId") Long teamId);

    int countApprovedByTeamId(@Param("teamId") Long teamId);
}
