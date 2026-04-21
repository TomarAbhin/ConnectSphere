package com.connectsphere.follow.repository;

import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.entity.FollowStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerIdAndFollowedId(Long followerId, Long followedId);

    boolean existsByFollowerIdAndFollowedIdAndStatus(Long followerId, Long followedId, FollowStatus status);

    List<Follow> findByFollowerIdAndStatusOrderByCreatedAtDesc(Long followerId, FollowStatus status);

    List<Follow> findByFollowedIdAndStatusOrderByCreatedAtDesc(Long followedId, FollowStatus status);

    List<Follow> findByFollowerIdInAndStatus(Collection<Long> followerIds, FollowStatus status);

    List<Follow> findByFollowedIdInAndStatus(Collection<Long> followedIds, FollowStatus status);

    long countByFollowerIdAndStatus(Long followerId, FollowStatus status);

    long countByFollowedIdAndStatus(Long followedId, FollowStatus status);

    List<Follow> findByFollowerIdAndFollowedIdInAndStatus(Long followerId, Collection<Long> followedIds, FollowStatus status);

    List<Follow> findByFollowedIdAndFollowerIdInAndStatus(Long followedId, Collection<Long> followerIds, FollowStatus status);

    List<Follow> findByFollowerIdAndStatusAndFollowedIdIn(Long followerId, FollowStatus status, Collection<Long> followedIds);

    List<Follow> findByFollowedIdAndStatusAndFollowerIdIn(Long followedId, FollowStatus status, Collection<Long> followerIds);
}
