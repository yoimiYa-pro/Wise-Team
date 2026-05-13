package com.teampm.mapper;

import com.teampm.domain.GlobalFceAhp;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GlobalFceAhpMapper {
    GlobalFceAhp findSingleton();

    int upsertSingleton(GlobalFceAhp row);
}
