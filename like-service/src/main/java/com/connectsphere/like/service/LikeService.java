package com.connectsphere.like.service;

import com.connectsphere.like.dto.ChangeReactionRequest;
import com.connectsphere.like.dto.HasLikedResponse;
import com.connectsphere.like.dto.LikeCountResponse;
import com.connectsphere.like.dto.LikeRequest;
import com.connectsphere.like.dto.LikeResponse;
import com.connectsphere.like.dto.LikeSummaryResponse;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import java.util.List;

public interface LikeService {

    LikeResponse likeTarget(String authorizationHeader, LikeRequest request);

    LikeResponse unlikeTarget(String authorizationHeader, TargetType targetType, Long targetId);

    HasLikedResponse hasLiked(String authorizationHeader, TargetType targetType, Long targetId);

    List<LikeResponse> getLikesByTarget(TargetType targetType, Long targetId);

    List<LikeResponse> getLikesByUser(Long userId);

    LikeCountResponse getLikeCount(TargetType targetType, Long targetId);

    LikeCountResponse getLikeCountByType(TargetType targetType, Long targetId, ReactionType reactionType);

    LikeSummaryResponse getReactionSummary(TargetType targetType, Long targetId);

    LikeResponse changeReaction(String authorizationHeader, Long likeId, ChangeReactionRequest request);
}
