package com.connectsphere.search.resource;

import com.connectsphere.search.dto.HashtagResponse;
import com.connectsphere.search.dto.IndexPostRequest;
import com.connectsphere.search.dto.PostSearchResponse;
import com.connectsphere.search.dto.UpsertHashtagRequest;
import com.connectsphere.search.dto.UserRole;
import com.connectsphere.search.dto.UserSearchResponse;
import com.connectsphere.search.service.SearchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class SearchResource {

    private final SearchService searchService;

    public SearchResource(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/index")
    public ResponseEntity<Void> indexPost(@Valid @RequestBody IndexPostRequest request) {
        searchService.indexPost(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/admin/index")
    public ResponseEntity<Void> indexPostForAdmin(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody IndexPostRequest request
    ) {
        searchService.indexPostForAdmin(authorization, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/admin/hashtags")
    public ResponseEntity<HashtagResponse> upsertHashtagForAdmin(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpsertHashtagRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(searchService.upsertHashtagForAdmin(authorization, request.tag()));
    }

    @DeleteMapping("/index/{postId}")
    public ResponseEntity<Void> removePostIndex(@PathVariable Long postId) {
        searchService.removePostIndex(postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts")
    public ResponseEntity<List<PostSearchResponse>> searchPosts(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "query", defaultValue = "") String query
    ) {
        return ResponseEntity.ok(searchService.searchPosts(authorization, query));
    }

    @GetMapping("/admin/posts")
    public ResponseEntity<List<PostSearchResponse>> searchPostsForAdmin(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "query", defaultValue = "") String query
    ) {
        return ResponseEntity.ok(searchService.searchPostsForAdmin(authorization, query));
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "role", required = false) UserRole role
    ) {
        return ResponseEntity.ok(searchService.searchUsers(authorization, query, role));
    }

    @GetMapping("/posts/{postId}/hashtags")
    public ResponseEntity<List<HashtagResponse>> getHashtagsForPost(@PathVariable Long postId) {
        return ResponseEntity.ok(searchService.getHashtagsForPost(postId));
    }

    @GetMapping("/hashtags/trending")
    public ResponseEntity<List<HashtagResponse>> getTrendingHashtags() {
        return ResponseEntity.ok(searchService.getTrendingHashtags());
    }

    @GetMapping("/hashtags/search")
    public ResponseEntity<List<HashtagResponse>> searchHashtags(
            @RequestParam(name = "query", defaultValue = "") String query
    ) {
        return ResponseEntity.ok(searchService.searchHashtags(query));
    }

    @GetMapping("/hashtags/{tag}/posts")
    public ResponseEntity<List<PostSearchResponse>> getPostsByHashtag(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String tag
    ) {
        return ResponseEntity.ok(searchService.getPostsByHashtag(authorization, tag));
    }

    @GetMapping("/hashtags/{tag}/count")
    public ResponseEntity<Long> getHashtagCount(@PathVariable String tag) {
        return ResponseEntity.ok(searchService.getHashtagCount(tag));
    }
}