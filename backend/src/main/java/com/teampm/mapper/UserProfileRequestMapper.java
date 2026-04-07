package com.teampm.mapper;

import com.teampm.domain.UserProfileRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserProfileRequestMapper {

    UserProfileRequest findById(@Param("id") Long id);

    UserProfileRequest findPendingByUserId(@Param("userId") Long userId);

    List<UserProfileRequest> findAllPending();

    int insert(UserProfileRequest row);

    int updateReview(UserProfileRequest row);
}
