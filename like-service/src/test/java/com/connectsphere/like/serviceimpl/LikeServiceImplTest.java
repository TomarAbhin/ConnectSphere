package com.connectsphere.like.serviceimpl;

import com.connectsphere.like.dto.AuthProfileResponse;
import com.connectsphere.like.dto.ChangeReactionRequest;
import com.connectsphere.like.dto.LikeRequest;
import com.connectsphere.like.entity.Like;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.repository.LikeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceImplTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private RestTemplate restTemplate;

    private LikeServiceImpl likeService;

    @BeforeEach
    void setUp() {
        likeService = new LikeServiceImpl(
                likeRepository,
                restTemplate,
                "http://auth",
                "http://post",
                "http://comment",
                "http://media",
                "http://notification"
        );
    }

    @Test
    void getReactionSummaryAggregatesCounts() {
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.LIKE)).thenReturn(3L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.LOVE)).thenReturn(2L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.HAHA)).thenReturn(1L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.WOW)).thenReturn(0L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.SAD)).thenReturn(0L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.ANGRY)).thenReturn(0L);
        when(likeRepository.countByTargetIdAndTargetType(7L, TargetType.POST)).thenReturn(6L);

        var response = likeService.getReactionSummary(TargetType.POST, 7L);

        assertEquals(6L, response.totalCount());
        assertEquals(3L, response.likeCount());
        assertEquals(2L, response.loveCount());
        assertEquals(1L, response.hahaCount());
    }

    @Test
    void likeTargetCreatesLikeAndUpdatesPostCount() {
        stubProfile(5L);
        when(restTemplate.exchange(eq("http://post/posts/10"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(5L, 10L, TargetType.POST)).thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> {
            Like like = invocation.getArgument(0);
            like.setLikeId(99L);
            return like;
        });
        when(restTemplate.exchange(eq("http://post/posts/10/likes"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));
        when(restTemplate.getForObject("http://post/posts/10", Map.class)).thenReturn(Map.of("authorId", 8L));

        var response = likeService.likeTarget("Bearer token", new LikeRequest(TargetType.POST, 10L, ReactionType.LOVE));

        assertEquals(99L, response.likeId());
        assertEquals(ReactionType.LOVE, response.reactionType());
        verify(restTemplate).postForObject(eq("http://notification/notifications"), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void unlikeAndChangeReactionUpdateCounts() {
        stubProfile(5L);
        Like like = like(44L, 5L, TargetType.COMMENT, 12L, ReactionType.LIKE);
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(5L, 12L, TargetType.COMMENT)).thenReturn(Optional.of(like));
        when(restTemplate.exchange(eq("http://comment/comments/12/likes"), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        assertEquals(ReactionType.LIKE, likeService.unlikeTarget("Bearer token", TargetType.COMMENT, 12L).reactionType());
        verify(likeRepository).delete(like);

        Like storyLike = like(45L, 5L, TargetType.STORY, 13L, ReactionType.SAD);
        when(likeRepository.findByLikeIdAndUserId(45L, 5L)).thenReturn(Optional.of(storyLike));
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.exchange(eq("http://media/stories/13/likes"), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));
        when(restTemplate.exchange(eq("http://media/stories/13/likes"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        assertEquals(ReactionType.WOW, likeService.changeReaction("Bearer token", 45L, new ChangeReactionRequest(ReactionType.WOW)).reactionType());
    }

    @Test
    void hasLikedAndListQueriesMapRepositoryResults() {
        stubProfile(5L);
        Like like = like(1L, 5L, TargetType.POST, 10L, ReactionType.HAHA);
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(5L, 10L, TargetType.POST)).thenReturn(Optional.of(like));
        when(likeRepository.findByTargetIdAndTargetTypeOrderByCreatedAtDesc(10L, TargetType.POST)).thenReturn(List.of(like));
        when(likeRepository.findByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(like));
        when(likeRepository.countByTargetIdAndTargetType(10L, TargetType.POST)).thenReturn(1L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(10L, TargetType.POST, ReactionType.HAHA)).thenReturn(1L);

        assertTrue(likeService.hasLiked("Bearer token", TargetType.POST, 10L).liked());
        assertEquals(1, likeService.getLikesByTarget(TargetType.POST, 10L).size());
        assertEquals(1, likeService.getLikesByUser(5L).size());
        assertEquals(1L, likeService.getLikeCount(TargetType.POST, 10L).count());
        assertEquals(1L, likeService.getLikeCountByType(TargetType.POST, 10L, ReactionType.HAHA).count());

        when(likeRepository.findByUserIdAndTargetIdAndTargetType(5L, 11L, TargetType.POST)).thenReturn(Optional.empty());
        assertFalse(likeService.hasLiked("Bearer token", TargetType.POST, 11L).liked());
    }

    private void stubProfile(Long userId) {
        AuthProfileResponse profile = new AuthProfileResponse(
                userId,
                "user" + userId,
                "user" + userId + "@example.com",
                "User " + userId,
                null,
                null,
                "USER",
                "LOCAL",
                true,
                Instant.parse("2026-05-05T00:00:00Z")
        );
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));
    }

    private Like like(Long likeId, Long userId, TargetType targetType, Long targetId, ReactionType reactionType) {
        Like like = new Like();
        like.setLikeId(likeId);
        like.setUserId(userId);
        like.setTargetType(targetType);
        like.setTargetId(targetId);
        like.setReactionType(reactionType);
        return like;
    }
}
