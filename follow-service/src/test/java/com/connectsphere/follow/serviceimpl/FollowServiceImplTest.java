package com.connectsphere.follow.serviceimpl;

import com.connectsphere.follow.dto.AuthProfileResponse;
import com.connectsphere.follow.dto.FollowResponse;
import com.connectsphere.follow.dto.UserSearchResponse;
import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.entity.FollowStatus;
import com.connectsphere.follow.repository.FollowRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private RestTemplate restTemplate;

    private FollowServiceImpl followService;

    @BeforeEach
    void setUp() {
        followService = new FollowServiceImpl(
                followRepository,
                restTemplate,
                "http://auth",
                "http://notification"
        );
    }

    @Test
    void followCreatesRelationshipWhenTargetUserExists() {
        AuthProfileResponse profile = profile(1L, "follower", "USER");
        UserSearchResponse targetUser = new UserSearchResponse(2L, "target", "Target User", null, "USER");

        stubProfile(profile);
        when(restTemplate.exchange(
                eq("http://auth/auth/search?query="),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserSearchResponse[].class)
        )).thenReturn(new ResponseEntity<>(new UserSearchResponse[]{profileToSearch(profile), targetUser}, HttpStatus.OK));
        when(followRepository.findByFollowerIdAndFollowedId(1L, 2L)).thenReturn(Optional.empty());
        when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FollowResponse response = followService.follow("Bearer token", 2L);

        assertNotNull(response);
        assertEquals(1L, response.followerId());
        assertEquals(2L, response.followedId());
        assertEquals(FollowStatus.ACTIVE, response.status());
    }

    @Test
    void unfollowStatusAndCountsUseRepository() {
        stubProfile(profile(1L, "follower", "USER"));
        Follow follow = follow(10L, 1L, 2L);
        when(followRepository.findByFollowerIdAndFollowedId(1L, 2L)).thenReturn(Optional.of(follow));
        when(followRepository.countByFollowedIdAndStatus(2L, FollowStatus.ACTIVE)).thenReturn(3L);
        when(followRepository.countByFollowerIdAndStatus(2L, FollowStatus.ACTIVE)).thenReturn(4L);
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(2L, FollowStatus.ACTIVE))
                .thenReturn(List.of(follow(11L, 2L, 5L)));
        when(followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(2L, FollowStatus.ACTIVE))
                .thenReturn(List.of(follow(12L, 5L, 2L)));

        assertTrue(followService.isFollowing("Bearer token", 2L).following());
        followService.unfollow("Bearer token", 2L);
        verify(followRepository).delete(follow);
        assertEquals(1L, followService.getCounts("Bearer token", 2L).mutualCount());

        when(followRepository.findByFollowerIdAndFollowedId(1L, 3L)).thenReturn(Optional.empty());
        assertFalse(followService.isFollowing("Bearer token", 3L).following());
    }

    @Test
    void followerFollowingAndMutualListsFetchUserSnapshots() {
        stubProfile(profile(1L, "me", "USER"));
        UserSearchResponse me = new UserSearchResponse(1L, "me", "Me", null, "USER");
        UserSearchResponse two = new UserSearchResponse(2L, "two", "Two", null, "USER");
        UserSearchResponse three = new UserSearchResponse(3L, "three", "Three", null, "USER");
        when(restTemplate.exchange(
                eq("http://auth/auth/search?query="),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserSearchResponse[].class)
        )).thenReturn(new ResponseEntity<>(new UserSearchResponse[]{me, two, three}, HttpStatus.OK));
        when(followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(1L, FollowStatus.ACTIVE))
                .thenReturn(List.of(follow(1L, 2L, 1L), follow(2L, 3L, 1L)));
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(1L, FollowStatus.ACTIVE))
                .thenReturn(List.of(follow(3L, 1L, 2L), follow(4L, 1L, 3L)));
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(2L, FollowStatus.ACTIVE))
                .thenReturn(List.of(follow(5L, 2L, 3L)));
        when(followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(2L, FollowStatus.ACTIVE))
                .thenReturn(List.of(follow(6L, 3L, 2L)));

        assertEquals(2, followService.getFollowers("Bearer token", 1L).size());
        assertEquals(2, followService.getFollowing("Bearer token", 1L).size());
        assertEquals(1, followService.getMutualFollows("Bearer token", 2L).size());
    }

    @Test
    void suggestedUsersRequireMutualConnectionsAndExcludeKnownUsers() {
        stubProfile(profile(1L, "me", "USER"));
        UserSearchResponse me = new UserSearchResponse(1L, "me", "Me", null, "USER");
        UserSearchResponse followed = new UserSearchResponse(2L, "followed", "Followed", null, "USER");
        UserSearchResponse candidate = new UserSearchResponse(3L, "candidate", "Candidate", null, "USER");
        when(restTemplate.exchange(
                eq("http://auth/auth/search?query="),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserSearchResponse[].class)
        )).thenReturn(new ResponseEntity<>(new UserSearchResponse[]{me, followed, candidate}, HttpStatus.OK));
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(1L, FollowStatus.ACTIVE))
                .thenReturn(List.of(follow(1L, 1L, 2L)));
        when(followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(1L, FollowStatus.ACTIVE))
                .thenReturn(List.of());
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(2L, FollowStatus.ACTIVE))
                .thenReturn(List.of(follow(2L, 2L, 3L)));

        var suggestions = followService.getSuggestedUsers("Bearer token");

        assertEquals(1, suggestions.size());
        assertEquals(3L, suggestions.get(0).userId());
        assertEquals(1L, suggestions.get(0).mutualConnectionCount());
    }

    private UserSearchResponse profileToSearch(AuthProfileResponse profile) {
        return new UserSearchResponse(profile.userId(), profile.username(), profile.fullName(), profile.profilePicUrl(), profile.role());
    }

    private void stubProfile(AuthProfileResponse profile) {
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));
    }

    private AuthProfileResponse profile(Long id, String username, String role) {
        return new AuthProfileResponse(
                id,
                username,
                username + "@example.com",
                "Full " + username,
                null,
                null,
                role,
                "LOCAL",
                true,
                Instant.parse("2026-05-05T00:00:00Z")
        );
    }

    private Follow follow(Long followId, Long followerId, Long followedId) {
        Follow follow = new Follow();
        follow.setFollowId(followId);
        follow.setFollowerId(followerId);
        follow.setFollowedId(followedId);
        follow.setStatus(FollowStatus.ACTIVE);
        return follow;
    }
}
