package com.teampm.mapper;

import com.teampm.domain.SkillOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SkillOptionMapper {

    SkillOption findById(@Param("id") Long id);

    List<SkillOption> findByTeamId(@Param("teamId") Long teamId);

    List<SkillOption> findAllTeamScoped();

    int insert(SkillOption row);

    int update(SkillOption row);

    int deleteById(@Param("id") Long id);
}
