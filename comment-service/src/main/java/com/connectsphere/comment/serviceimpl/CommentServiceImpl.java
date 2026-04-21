package com.connectsphere.comment.serviceimpl;

import com.connectsphere.comment.dto.AddCommentRequest;
import com.connectsphere.comment.dto.AuthProfileResponse;
import com.connectsphere.comment.dto.CommentResponse;
import com.connectsphere.comment.dto.UpdateCommentRequest;
import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.repository.CommentRepository;
import com.connectsphere.comment.service.CommentService;
import java.net.URI;
import java.util.List;
import java.util.Objects;
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
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final RestTemplate restTemplate;
    private final String authServiceUrl;
    private final String postServiceUrl;

    public CommentServiceImpl(
            CommentRepository commentRepository,
            RestTemplate restTemplate,
            @Value("${app.services.auth-service.url:http://localhost:8081}") String authServiceUrl,
            @Value("${app.services.post-service.url:http://localhost:8082}") String postServiceUrl
    ) {
        this.commentRepository = commentRepository;
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
        this.postServiceUrl = postServiceUrl;
    }

    @Override
    public CommentResponse addComment(String authorizationHeader, AddCommentRequest request) {
        Long authorId = resolveCurrentUserId(authorizationHeader);
        ensurePostExists(authorizationHeader, request.postId());
        ensureParentCommentValid(request);

        Comment comment = new Comment();
        comment.setPostId(request.postId());
        comment.setAuthorId(authorId);
        comment.setParentCommentId(request.parentCommentId());
        comment.setContent(request.content().trim());

        Comment saved = commentRepository.save(comment);
        incrementPostCommentCount(authorizationHeader, request.postId());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByPost(Long postId) {
        return commentRepository.findTopLevelByPostId(postId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByUser(Long userId) {
        return commentRepository.findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getReplies(Long parentCommentId) {
        Comment parent = commentRepository.findByCommentId(parentCommentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        return commentRepository.findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(parent.getCommentId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CommentResponse updateComment(String authorizationHeader, Long commentId, UpdateCommentRequest request) {
        Comment comment = getOwnedComment(authorizationHeader, commentId);
        comment.setContent(request.content().trim());
        return toResponse(commentRepository.save(comment));
    }

    @Override
    public void deleteComment(String authorizationHeader, Long commentId) {
        Comment comment = getOwnedComment(authorizationHeader, commentId);
        if (!comment.isDeleted()) {
            comment.setDeleted(true);
            commentRepository.save(comment);
            decrementPostCommentCount(authorizationHeader, comment.getPostId());
        }
    }

    @Override
    public CommentResponse likeComment(Long commentId) {
        Comment comment = getActiveComment(commentId);
        comment.setLikesCount(comment.getLikesCount() + 1);
        return toResponse(commentRepository.save(comment));
    }

    @Override
    public CommentResponse unlikeComment(Long commentId) {
        Comment comment = getActiveComment(commentId);
        comment.setLikesCount(Math.max(0, comment.getLikesCount() - 1));
        return toResponse(commentRepository.save(comment));
    }

    @Override
    @Transactional(readOnly = true)
    public long getCommentCount(Long postId) {
        return commentRepository.countByPostIdAndDeletedFalse(postId);
    }

    private Comment getActiveComment(Long commentId) {
        return commentRepository.findByCommentIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
    }

    private Comment getOwnedComment(String authorizationHeader, Long commentId) {
        Comment comment = getActiveComment(commentId);
        Long requesterId = resolveCurrentUserId(authorizationHeader);
        if (!Objects.equals(comment.getAuthorId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only modify your own comment");
        }
        return comment;
    }

    private void ensurePostExists(String authorizationHeader, Long postId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            restTemplate.exchange(postServiceUrl + "/posts/" + postId, HttpMethod.GET, request, String.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found or not accessible");
        }
    }

    private void ensureParentCommentValid(AddCommentRequest request) {
        if (request.parentCommentId() == null) {
            return;
        }

        Comment parent = getActiveComment(request.parentCommentId());
        if (!Objects.equals(parent.getPostId(), request.postId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply must belong to the same post");
        }
        if (parent.getParentCommentId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replies are only allowed two levels deep");
        }
    }

    private Long resolveCurrentUserId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            AuthProfileResponse profile = restTemplate.exchange(
                    authServiceUrl + "/auth/profile",
                    HttpMethod.GET,
                    request,
                    AuthProfileResponse.class
            ).getBody();

            if (profile == null || profile.userId() == null || !profile.active()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
            }
            return profile.userId();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
    }

    private void incrementPostCommentCount(String authorizationHeader, Long postId) {
        updatePostCommentCount(authorizationHeader, postId, true);
    }

    private void decrementPostCommentCount(String authorizationHeader, Long postId) {
        updatePostCommentCount(authorizationHeader, postId, false);
    }

    private void updatePostCommentCount(String authorizationHeader, Long postId, boolean increment) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            if (increment) {
                restTemplate.exchange(postServiceUrl + "/posts/" + postId + "/comments", HttpMethod.POST, request, String.class);
            } else {
                restTemplate.exchange(postServiceUrl + "/posts/" + postId + "/comments", HttpMethod.DELETE, request, String.class);
            }
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to update post comment count");
        }
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getCommentId(),
                comment.getPostId(),
                comment.getAuthorId(),
                comment.getParentCommentId(),
                comment.getContent(),
                comment.getLikesCount(),
                comment.isDeleted(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
