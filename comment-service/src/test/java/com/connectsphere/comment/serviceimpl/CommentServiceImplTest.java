package com.connectsphere.comment.serviceimpl;

import com.connectsphere.comment.dto.AddCommentRequest;
import com.connectsphere.comment.dto.AuthProfileResponse;
import com.connectsphere.comment.dto.UpdateCommentRequest;
import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.repository.CommentRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

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

    @Test
    void addTopLevelCommentStoresSnapshotAndNotifiesPostAuthor() {
        stubCurrentProfile();
        when(restTemplate.exchange(eq("http://post/posts/10"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            saved.setCommentId(77L);
            return saved;
        });
        when(restTemplate.exchange(eq("http://post/posts/10/comments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));
        when(restTemplate.getForObject("http://post/posts/10", Map.class)).thenReturn(Map.of("authorId", 5L));

        var response = commentService.addComment("Bearer token", new AddCommentRequest(10L, null, " Hello "));

        assertEquals(77L, response.commentId());
        assertEquals("Hello", response.content());
        assertEquals("tester", response.authorUsername());
        verify(restTemplate).postForObject(eq("http://notification/notifications"), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void replyMustBelongToSamePostAndOnlyTwoLevelsDeep() {
        stubCurrentProfile();
        when(restTemplate.exchange(eq("http://post/posts/10"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        Comment parent = comment(1L, 11L, 5L, null, "Parent");
        when(commentRepository.findByCommentIdAndDeletedFalse(1L)).thenReturn(Optional.of(parent));

        assertThrows(ResponseStatusException.class, () ->
                commentService.addComment("Bearer token", new AddCommentRequest(10L, 1L, "Reply")));

        parent.setPostId(10L);
        parent.setParentCommentId(99L);
        assertThrows(ResponseStatusException.class, () ->
                commentService.addComment("Bearer token", new AddCommentRequest(10L, 1L, "Reply")));
    }

    @Test
    void queryUpdateDeleteAndCountsUseRepository() {
        stubCurrentProfile();
        Comment comment = comment(3L, 10L, 1L, null, "Old");
        when(commentRepository.findByCommentIdAndDeletedFalse(3L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentRepository.findTopLevelByPostId(10L)).thenReturn(List.of(comment));
        when(commentRepository.findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(1L)).thenReturn(List.of(comment));
        when(commentRepository.findByCommentId(3L)).thenReturn(Optional.of(comment));
        when(commentRepository.findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(3L)).thenReturn(List.of(comment(4L, 10L, 2L, 3L, "Reply")));
        when(commentRepository.countByPostIdAndDeletedFalse(10L)).thenReturn(2L);
        when(restTemplate.exchange(eq("http://post/posts/10/comments"), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        assertEquals("Old", commentService.getCommentById(3L).content());
        assertEquals(1, commentService.getCommentsByPost(10L).size());
        assertEquals(1, commentService.getCommentsByUser(1L).size());
        assertEquals(1, commentService.getReplies(3L).size());
        assertEquals("New", commentService.updateComment("Bearer token", 3L, new UpdateCommentRequest(" New ")).content());
        commentService.deleteComment("Bearer token", 3L);
        assertEquals(2L, commentService.getCommentCount(10L));
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

    private Comment comment(Long commentId, Long postId, Long authorId, Long parentCommentId, String content) {
        Comment comment = new Comment();
        comment.setCommentId(commentId);
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setAuthorUsername("tester");
        comment.setAuthorFullName("Test User");
        comment.setParentCommentId(parentCommentId);
        comment.setContent(content);
        comment.setLikesCount(0L);
        comment.setDeleted(false);
        return comment;
    }
}
