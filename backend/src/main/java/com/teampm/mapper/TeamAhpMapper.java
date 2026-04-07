package com.teampm.mapper;

import com.teampm.domain.TeamAhp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeamAhpMapper {
    TeamAhp findByTeamId(@Param("teamId") Long teamId);

    int upsert(TeamAhp row);
}
