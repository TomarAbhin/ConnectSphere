package com.connectsphere.follow.serviceimpl;

import com.connectsphere.follow.dto.AuthProfileResponse;
import com.connectsphere.follow.dto.FollowResponse;
import com.connectsphere.follow.dto.UserSearchResponse;
import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.entity.FollowStatus;
import com.connectsphere.follow.repository.FollowRepository;
import java.time.Instant;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        AuthProfileResponse profile = new AuthProfileResponse(
                1L,
                "follower",
                "follower@example.com",
                "Follower User",
                null,
                null,
                "USER",
                "LOCAL",
                true,
                Instant.parse("2026-05-05T00:00:00Z")
        );
        UserSearchResponse targetUser = new UserSearchResponse(2L, "target", "Target User", null, "USER");

        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));
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

    private UserSearchResponse profileToSearch(AuthProfileResponse profile) {
        return new UserSearchResponse(profile.userId(), profile.username(), profile.fullName(), profile.profilePicUrl(), profile.role());
    }
}