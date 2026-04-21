package com.connectsphere.like.dto;

import com.connectsphere.like.entity.TargetType;

public record LikeSummaryResponse(
        TargetType targetType,
        Long targetId,
        long totalCount,
        long likeCount,
        long loveCount,
        long hahaCount,
        long wowCount,
        long sadCount,
        long angryCount
) {
}
