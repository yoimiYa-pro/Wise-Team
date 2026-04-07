package com.teampm.mapper;

import com.teampm.domain.Team;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeamMapper {
    Team findById(@Param("id") Long id);

    List<Team> findByManagerId(@Param("managerId") Long managerId);

    List<Team> findAll();

    List<Team> findTeamsForApprovedMember(@Param("userId") Long userId);

    int insert(Team team);

    int update(Team team);

    int deleteById(@Param("id") Long id);
}
