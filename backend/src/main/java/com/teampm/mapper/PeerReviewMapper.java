package com.teampm.mapper;

import com.teampm.domain.PeerReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PeerReviewMapper {
    List<PeerReview> findByCycleAndTarget(@Param("cycleId") Long cycleId, @Param("targetUserId") Long targetUserId);

    int insert(PeerReview r);

    int countByReviewerInCycle(@Param("cycleId") Long cycleId, @Param("reviewerUserId") Long reviewerUserId, @Param("targetUserId") Long targetUserId);
}
