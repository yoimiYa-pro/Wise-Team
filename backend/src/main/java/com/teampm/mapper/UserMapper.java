package com.teampm.mapper;

import com.teampm.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    User findById(@Param("id") Long id);

    User findByUsername(@Param("username") String username);

    List<User> findAll();

    long countForAdminList(@Param("keyword") String keyword);

    List<User> findPageForAdminList(
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit);

    int insert(User user);

    int update(User user);

    int updatePerformance(@Param("id") Long id, @Param("avgPerformance") java.math.BigDecimal avgPerformance);

    int updateDelayHistory(@Param("id") Long id, @Param("score") java.math.BigDecimal score);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
