package com.connectsphere.like.repository;

import com.connectsphere.like.entity.Like;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, TargetType targetType);

    Optional<Like> findByLikeIdAndUserId(Long likeId, Long userId);

    List<Like> findByTargetIdAndTargetTypeOrderByCreatedAtDesc(Long targetId, TargetType targetType);

    List<Like> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, TargetType targetType);

    long countByTargetIdAndTargetType(Long targetId, TargetType targetType);

    long countByTargetIdAndTargetTypeAndReactionType(Long targetId, TargetType targetType, ReactionType reactionType);

    void deleteByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, TargetType targetType);
}
