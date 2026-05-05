package com.connectsphere.post.service;

import com.connectsphere.post.dto.CreatePostRequest;
import com.connectsphere.post.dto.PostResponse;
import com.connectsphere.post.dto.UpdatePostRequest;
import com.connectsphere.post.entity.PostVisibility;
import java.util.List;

public interface PostService {

    PostResponse createPost(String authorizationHeader, CreatePostRequest request);

    PostResponse getPostById(String authorizationHeader, Long postId);

    List<PostResponse> getPostsByUser(String authorizationHeader, Long userId);

    List<PostResponse> getFeedForUser(String authorizationHeader);

    PostResponse updatePost(String authorizationHeader, Long postId, UpdatePostRequest request);

    void deletePost(String authorizationHeader, Long postId);

    List<PostResponse> searchPosts(String authorizationHeader, String query);

    void deletePostAsAdmin(String authorizationHeader, Long postId);

    void deletePostsByAuthor(String authorizationHeader, Long authorId);

    PostResponse incrementLikes(Long postId);

    PostResponse decrementLikes(Long postId);

    PostResponse incrementComments(Long postId);

    PostResponse decrementComments(Long postId);

    PostResponse changeVisibility(String authorizationHeader, Long postId, PostVisibility visibility);

    long getPostCount();

    /**
     * Backfill author snapshot fields for posts where snapshot is missing.
     * Accessible to admins only.
     */
    void backfillAuthorSnapshots(String authorizationHeader);
}
