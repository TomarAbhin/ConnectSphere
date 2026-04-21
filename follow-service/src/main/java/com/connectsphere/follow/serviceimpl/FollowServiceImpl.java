package com.connectsphere.follow.serviceimpl;

import com.connectsphere.follow.dto.AuthProfileResponse;
import com.connectsphere.follow.dto.FollowCountResponse;
import com.connectsphere.follow.dto.FollowResponse;
import com.connectsphere.follow.dto.FollowStatusResponse;
import com.connectsphere.follow.dto.FollowerResponse;
import com.connectsphere.follow.dto.FollowingResponse;
import com.connectsphere.follow.dto.SuggestedUserResponse;
import com.connectsphere.follow.dto.UserSearchResponse;
import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.entity.FollowStatus;
import com.connectsphere.follow.repository.FollowRepository;
import com.connectsphere.follow.service.FollowService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final RestTemplate restTemplate;
    private final String authServiceUrl;

    public FollowServiceImpl(
            FollowRepository followRepository,
            RestTemplate restTemplate,
            @Value("${app.services.auth-service.url:http://localhost:8081}") String authServiceUrl
    ) {
        this.followRepository = followRepository;
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
    }

    @Override
    public FollowResponse follow(String authorizationHeader, Long followedId) {
        Long followerId = resolveCurrentUserId(authorizationHeader);
        validateTargetUserExists(authorizationHeader, followedId);
        if (Objects.equals(followerId, followedId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot follow yourself");
        }

        Follow existing = followRepository.findByFollowerIdAndFollowedId(followerId, followedId).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFollowedId(followedId);
        follow.setStatus(FollowStatus.ACTIVE);
        return toResponse(followRepository.save(follow));
    }

    @Override
    public void unfollow(String authorizationHeader, Long followedId) {
        Long followerId = resolveCurrentUserId(authorizationHeader);
        Follow follow = followRepository.findByFollowerIdAndFollowedId(followerId, followedId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Follow relationship not found"));
        followRepository.delete(follow);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowStatusResponse isFollowing(String authorizationHeader, Long followedId) {
        Long followerId = resolveCurrentUserId(authorizationHeader);
        return followRepository.findByFollowerIdAndFollowedId(followerId, followedId)
                .map(follow -> new FollowStatusResponse(follow.getStatus() == FollowStatus.ACTIVE, follow.getFollowId(), follow.getStatus()))
                .orElse(new FollowStatusResponse(false, null, null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowerResponse> getFollowers(String authorizationHeader, Long userId) {
        return followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId, FollowStatus.ACTIVE)
                .stream()
                .map(follow -> toFollowerResponse(fetchUserById(authorizationHeader, follow.getFollowerId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowingResponse> getFollowing(String authorizationHeader, Long userId) {
        return followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(userId, FollowStatus.ACTIVE)
                .stream()
                .map(follow -> toFollowingResponse(fetchUserById(authorizationHeader, follow.getFollowedId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FollowCountResponse getCounts(String authorizationHeader, Long userId) {
        long followerCount = followRepository.countByFollowedIdAndStatus(userId, FollowStatus.ACTIVE);
        long followingCount = followRepository.countByFollowerIdAndStatus(userId, FollowStatus.ACTIVE);
        long mutualCount = getMutualUserIds(userId).size();
        return new FollowCountResponse(userId, followerCount, followingCount, mutualCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowerResponse> getMutualFollows(String authorizationHeader, Long userId) {
        Long currentUserId = resolveCurrentUserId(authorizationHeader);
        Set<Long> currentFollowing = followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(currentUserId, FollowStatus.ACTIVE)
                .stream()
                .map(Follow::getFollowedId)
                .collect(Collectors.toSet());
        Set<Long> currentFollowers = followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(currentUserId, FollowStatus.ACTIVE)
                .stream()
                .map(Follow::getFollowerId)
                .collect(Collectors.toSet());

        Set<Long> targetFollowing = followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(userId, FollowStatus.ACTIVE)
                .stream()
                .map(Follow::getFollowedId)
                .collect(Collectors.toSet());
        Set<Long> targetFollowers = followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId, FollowStatus.ACTIVE)
                .stream()
                .map(Follow::getFollowerId)
                .collect(Collectors.toSet());

        Set<Long> mutualUserIds = new LinkedHashSet<>(currentFollowing);
        mutualUserIds.retainAll(targetFollowing);
        mutualUserIds.addAll(intersection(currentFollowers, targetFollowers));

        return mutualUserIds.stream()
                .map(userIdValue -> toFollowerResponse(fetchUserById(authorizationHeader, userIdValue)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuggestedUserResponse> getSuggestedUsers(String authorizationHeader) {
        Long currentUserId = resolveCurrentUserId(authorizationHeader);
        List<UserSearchResponse> allUsers = fetchAllUsers(authorizationHeader);

        Set<Long> followingIds = followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(currentUserId, FollowStatus.ACTIVE)
                .stream()
                .map(Follow::getFollowedId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> followerIds = followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(currentUserId, FollowStatus.ACTIVE)
                .stream()
                .map(Follow::getFollowerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, Long> mutualCounts = new HashMap<>();
        for (Long followingId : followingIds) {
            List<Follow> secondDegree = followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(followingId, FollowStatus.ACTIVE);
            for (Follow follow : secondDegree) {
                Long candidateId = follow.getFollowedId();
                if (Objects.equals(candidateId, currentUserId) || followingIds.contains(candidateId)) {
                    continue;
                }
                mutualCounts.merge(candidateId, 1L, Long::sum);
            }
        }

        Set<Long> excludedIds = new HashSet<>(followingIds);
        excludedIds.addAll(followerIds);
        excludedIds.add(currentUserId);

        return allUsers.stream()
                .filter(user -> !excludedIds.contains(user.userId()))
                .map(user -> new SuggestedUserResponse(
                        user.userId(),
                        user.username(),
                        user.fullName(),
                        user.profilePicUrl(),
                        user.role(),
                        mutualCounts.getOrDefault(user.userId(), 0L)
                ))
                .filter(response -> response.mutualConnectionCount() > 0)
                .sorted(Comparator.comparingLong(SuggestedUserResponse::mutualConnectionCount).reversed()
                        .thenComparing(SuggestedUserResponse::username, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private Set<Long> getMutualUserIds(Long userId) {
        Set<Long> following = followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(userId, FollowStatus.ACTIVE)
                .stream()
                .map(Follow::getFollowedId)
                .collect(Collectors.toSet());
        Set<Long> followers = followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId, FollowStatus.ACTIVE)
                .stream()
                .map(Follow::getFollowerId)
                .collect(Collectors.toSet());
        return intersection(following, followers);
    }

    private Set<Long> intersection(Collection<Long> left, Collection<Long> right) {
        Set<Long> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private Long resolveCurrentUserId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            AuthProfileResponse profile = fetchProfile(authorizationHeader);
            if (profile == null || profile.userId() == null || !profile.active()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
            }
            return profile.userId();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
    }

    private void validateTargetUserExists(String authorizationHeader, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user id is required");
        }
        fetchUserById(authorizationHeader, userId);
    }

    private AuthProfileResponse fetchProfile(String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authorizationHeader);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        return restTemplate.exchange(
                authServiceUrl + "/auth/profile",
                HttpMethod.GET,
                request,
                AuthProfileResponse.class
        ).getBody();
    }

    private UserSearchResponse fetchUserById(String authorizationHeader, Long userId) {
        List<UserSearchResponse> allUsers = fetchAllUsers(authorizationHeader);
        return allUsers.stream()
                .filter(user -> Objects.equals(user.userId(), userId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private List<UserSearchResponse> fetchAllUsers(String authorizationHeader) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                headers.set("Authorization", authorizationHeader);
            }
            HttpEntity<Void> request = new HttpEntity<>(headers);
            UserSearchResponse[] users = restTemplate.exchange(
                    authServiceUrl + "/auth/search?query=",
                    HttpMethod.GET,
                    request,
                    UserSearchResponse[].class
            ).getBody();
            if (users == null) {
                return List.of();
            }
            return List.of(users);
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    private FollowerResponse toFollowerResponse(UserSearchResponse user) {
        return new FollowerResponse(user.userId(), user.username(), user.fullName(), user.profilePicUrl(), user.role());
    }

    private FollowingResponse toFollowingResponse(UserSearchResponse user) {
        return new FollowingResponse(user.userId(), user.username(), user.fullName(), user.profilePicUrl(), user.role());
    }

    private FollowResponse toResponse(Follow follow) {
        return new FollowResponse(
                follow.getFollowId(),
                follow.getFollowerId(),
                follow.getFollowedId(),
                follow.getStatus(),
                follow.getCreatedAt(),
                follow.getUpdatedAt()
        );
    }
}
