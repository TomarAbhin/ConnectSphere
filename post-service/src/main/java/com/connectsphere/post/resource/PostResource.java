package com.connectsphere.post.resource;

import com.connectsphere.post.dto.CreatePostRequest;
import com.connectsphere.post.dto.PostCountResponse;
import com.connectsphere.post.dto.PostResponse;
import com.connectsphere.post.dto.PostVisibilityRequest;
import com.connectsphere.post.dto.UpdatePostRequest;
import com.connectsphere.post.service.PostService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts")
public class PostResource {

    private final PostService postService;

    public PostResource(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreatePostRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(authorization, request));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPostById(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(postService.getPostById(authorization, postId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostResponse>> getPostsByUser(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(postService.getPostsByUser(authorization, userId));
    }

    @GetMapping("/feed")
    public ResponseEntity<List<PostResponse>> getFeed(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ResponseEntity.ok(postService.getFeedForUser(authorization));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostRequest request
    ) {
        return ResponseEntity.ok(postService.updatePost(authorization, postId, request));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long postId
    ) {
        postService.deletePost(authorization, postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/{postId}")
    public ResponseEntity<Void> deletePostAsAdmin(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long postId
    ) {
        postService.deletePostAsAdmin(authorization, postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/author/{authorId}")
    public ResponseEntity<Void> deletePostsByAuthor(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long authorId
    ) {
        postService.deletePostsByAuthor(authorization, authorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<PostResponse>> searchPosts(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "query", defaultValue = "") String query
    ) {
        return ResponseEntity.ok(postService.searchPosts(authorization, query));
    }

    @PostMapping("/{postId}/likes")
    public ResponseEntity<PostResponse> incrementLikes(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.incrementLikes(postId));
    }

    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<PostResponse> decrementLikes(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.decrementLikes(postId));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<PostResponse> incrementComments(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.incrementComments(postId));
    }

    @DeleteMapping("/{postId}/comments")
    public ResponseEntity<PostResponse> decrementComments(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.decrementComments(postId));
    }

    @PutMapping("/{postId}/visibility")
    public ResponseEntity<PostResponse> changeVisibility(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long postId,
            @Valid @RequestBody PostVisibilityRequest request
    ) {
        return ResponseEntity.ok(postService.changeVisibility(authorization, postId, request.visibility()));
    }

    @GetMapping("/count")
    public ResponseEntity<PostCountResponse> getPostCount() {
        return ResponseEntity.ok(new PostCountResponse(postService.getPostCount()));
    }

    @PostMapping("/admin/backfill-author-snapshots")
    public ResponseEntity<Void> backfillAuthorSnapshots(
            @org.springframework.web.bind.annotation.RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        postService.backfillAuthorSnapshots(authorization);
        return ResponseEntity.noContent().build();
    }
}
