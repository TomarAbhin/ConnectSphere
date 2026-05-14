package com.connectsphere.comment.serviceimpl;

import com.connectsphere.comment.dto.AuthProfileResponse;
import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.repository.CommentRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private RestTemplate restTemplate;

    private CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentServiceImpl(
                commentRepository,
                restTemplate,
                "http://auth",
                "http://post",
                "http://notification"
        );
    }

    @Test
    void likeCommentIncrementsLikeCount() {
        stubCurrentProfile();

        Comment comment = new Comment();
        comment.setCommentId(1L);
        comment.setPostId(10L);
        comment.setAuthorId(5L);
        comment.setContent("Hello");
        comment.setLikesCount(3L);
        comment.setDeleted(false);

        when(commentRepository.findByCommentIdAndDeletedFalse(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = commentService.likeComment("Bearer token", 1L);

        assertEquals(4L, response.likesCount());
    }

    @Test
    void unlikeCommentDoesNotGoBelowZero() {
        stubCurrentProfile();

        Comment comment = new Comment();
        comment.setCommentId(2L);
        comment.setPostId(10L);
        comment.setAuthorId(5L);
        comment.setContent("Hello");
        comment.setLikesCount(0L);
        comment.setDeleted(false);

        when(commentRepository.findByCommentIdAndDeletedFalse(2L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = commentService.unlikeComment("Bearer token", 2L);

        assertEquals(0L, response.likesCount());
    }

    private void stubCurrentProfile() {
        AuthProfileResponse profile = new AuthProfileResponse(
                1L,
                "tester",
                "tester@example.com",
                "Test User",
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
}