package com.connectsphere.comment.resource;

import com.connectsphere.comment.dto.AddCommentRequest;
import com.connectsphere.comment.dto.CommentCountResponse;
import com.connectsphere.comment.dto.CommentResponse;
import com.connectsphere.comment.dto.UpdateCommentRequest;
import com.connectsphere.comment.service.CommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comments")
public class CommentResource {

    private final CommentService commentService;

    public CommentResource(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/{commentId}")
    public ResponseEntity<CommentResponse> getCommentById(@PathVariable Long commentId) {
        return ResponseEntity.ok(commentService.getCommentById(commentId));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> addComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody AddCommentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.addComment(authorization, request));
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getCommentsByPost(postId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(commentService.getCommentsByUser(userId));
    }

    @GetMapping("/{commentId}/replies")
    public ResponseEntity<List<CommentResponse>> getReplies(@PathVariable Long commentId) {
        return ResponseEntity.ok(commentService.getReplies(commentId));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request
    ) {
        return ResponseEntity.ok(commentService.updateComment(authorization, commentId, request));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(authorization, commentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{commentId}/likes")
    public ResponseEntity<CommentResponse> likeComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long commentId
    ) {
        return ResponseEntity.ok(commentService.likeComment(authorization, commentId));
    }

    @DeleteMapping("/{commentId}/likes")
    public ResponseEntity<CommentResponse> unlikeComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long commentId
    ) {
        return ResponseEntity.ok(commentService.unlikeComment(authorization, commentId));
    }

    @GetMapping("/post/{postId}/count")
    public ResponseEntity<CommentCountResponse> getCommentCount(@PathVariable Long postId) {
        return ResponseEntity.ok(new CommentCountResponse(commentService.getCommentCount(postId)));
    }
}
