package com.teampm.mapper;

import com.teampm.domain.InAppMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InAppMessageMapper {

    InAppMessage findById(@Param("id") Long id);

    List<InAppMessage> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    int insert(InAppMessage row);

    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    int markAllRead(@Param("userId") Long userId);

    int countUnread(@Param("userId") Long userId);
}
