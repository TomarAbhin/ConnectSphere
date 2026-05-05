package com.connectsphere.search.service;

import com.connectsphere.search.dto.HashtagResponse;
import com.connectsphere.search.dto.IndexPostRequest;
import com.connectsphere.search.dto.PostSearchResponse;
import com.connectsphere.search.dto.UserRole;
import com.connectsphere.search.dto.UserSearchResponse;
import java.util.List;

public interface SearchService {

    void indexPost(IndexPostRequest request);

    void indexPostForAdmin(String authorizationHeader, IndexPostRequest request);

    HashtagResponse upsertHashtagForAdmin(String authorizationHeader, String tag);

    void removePostIndex(Long postId);

    List<PostSearchResponse> searchPosts(String authorizationHeader, String query);

    List<PostSearchResponse> searchPostsForAdmin(String authorizationHeader, String query);

    List<UserSearchResponse> searchUsers(String authorizationHeader, String query, UserRole role);

    List<HashtagResponse> getHashtagsForPost(Long postId);

    List<HashtagResponse> getTrendingHashtags();

    List<PostSearchResponse> getPostsByHashtag(String authorizationHeader, String tag);

    List<HashtagResponse> searchHashtags(String query);

    long getHashtagCount(String tag);
}