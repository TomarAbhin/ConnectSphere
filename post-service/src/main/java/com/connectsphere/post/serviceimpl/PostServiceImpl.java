package com.connectsphere.post.serviceimpl;

import com.connectsphere.post.dto.AuthProfileResponse;
import com.connectsphere.post.dto.CreatePostRequest;
import com.connectsphere.post.dto.PostResponse;
import com.connectsphere.post.dto.UpdatePostRequest;
import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.PostVisibility;
import com.connectsphere.post.repository.PostRepository;
import com.connectsphere.post.service.PostService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Transactional
public class PostServiceImpl implements PostService {

    private static final Collection<PostVisibility> FEED_VISIBILITIES = List.of(
            PostVisibility.PUBLIC,
            PostVisibility.FOLLOWERS_ONLY
    );

    private final PostRepository postRepository;
    private final RestTemplate restTemplate;
    private final String followServiceUrl;
    private final String followServicePath;
    private final String authServiceUrl;

    public PostServiceImpl(
            PostRepository postRepository,
            RestTemplate restTemplate,
            @Value("${app.services.follow-service.url:http://localhost:8085}") String followServiceUrl,
            @Value("${app.services.follow-service.following-path:/follows/{userId}/following-ids}") String followServicePath,
            @Value("${app.services.auth-service.url:http://localhost:8081}") String authServiceUrl
    ) {
        this.postRepository = postRepository;
        this.restTemplate = restTemplate;
        this.followServiceUrl = followServiceUrl;
        this.followServicePath = followServicePath;
        this.authServiceUrl = authServiceUrl;
    }

    @Override
    public PostResponse createPost(String authorizationHeader, CreatePostRequest request) {
        Long authorId = resolveCurrentUserId(authorizationHeader);
        Post post = new Post();
        post.setAuthorId(authorId);
        post.setContent(request.content().trim());
        post.setMediaUrls(normalizeMediaUrls(request.mediaUrls()));
        post.setPostType(request.postType() == null ? inferType(post.getMediaUrls()) : request.postType());
        post.setVisibility(request.visibility() == null ? PostVisibility.PUBLIC : request.visibility());
        return toResponse(postRepository.save(post));
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(String authorizationHeader, Long postId) {
        Long requesterId = resolveCurrentUserId(authorizationHeader);
        Post post = getActivePost(postId);
        if (!canViewPost(post, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this post");
        }
        return toResponse(post);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getPostsByUser(String authorizationHeader, Long userId) {
        Long requesterId = resolveCurrentUserId(authorizationHeader);
        return postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .filter(post -> canViewPost(post, requesterId))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getFeedForUser(String authorizationHeader) {
        Long requesterId = resolveCurrentUserId(authorizationHeader);
        Set<Long> followingIds = new LinkedHashSet<>(fetchFollowingIds(requesterId));

        List<Post> feedPosts = new ArrayList<>();
        if (!followingIds.isEmpty()) {
            feedPosts.addAll(postRepository.findByAuthorIdInAndVisibilityInAndIsDeletedFalseOrderByCreatedAtDesc(
                    followingIds,
                    FEED_VISIBILITIES
            ));
        }

        feedPosts.addAll(postRepository.findByAuthorIdAndVisibilityInAndIsDeletedFalseOrderByCreatedAtDesc(
                requesterId,
                List.of(PostVisibility.PUBLIC, PostVisibility.FOLLOWERS_ONLY, PostVisibility.PRIVATE)
        ));

        if (feedPosts.isEmpty()) {
            feedPosts.addAll(postRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(PostVisibility.PUBLIC));
        }

        return feedPosts.stream()
                .distinct()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PostResponse updatePost(String authorizationHeader, Long postId, UpdatePostRequest request) {
        Post post = getOwnedPost(authorizationHeader, postId);

        if (request.content() != null) {
            post.setContent(request.content().trim());
        }
        if (request.mediaUrls() != null) {
            post.setMediaUrls(normalizeMediaUrls(request.mediaUrls()));
            post.setPostType(request.postType() == null ? inferType(post.getMediaUrls()) : request.postType());
        } else if (request.postType() != null) {
            post.setPostType(request.postType());
        }
        if (request.visibility() != null) {
            post.setVisibility(request.visibility());
        }

        return toResponse(postRepository.save(post));
    }

    @Override
    public void deletePost(String authorizationHeader, Long postId) {
        Post post = getOwnedPost(authorizationHeader, postId);
        post.setDeleted(true);
        postRepository.save(post);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> searchPosts(String authorizationHeader, String query) {
        Long requesterId = resolveCurrentUserId(authorizationHeader);
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) {
            return postRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(PostVisibility.PUBLIC)
                    .stream()
                    .filter(post -> canViewPost(post, requesterId))
                    .map(this::toResponse)
                    .toList();
        }
        return postRepository.searchByContent(safeQuery)
                .stream()
                .filter(post -> canViewPost(post, requesterId))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PostResponse incrementLikes(Long postId) {
        Post post = getActivePost(postId);
        post.setLikesCount(post.getLikesCount() + 1);
        return toResponse(postRepository.save(post));
    }

    @Override
    public PostResponse decrementLikes(Long postId) {
        Post post = getActivePost(postId);
        post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
        return toResponse(postRepository.save(post));
    }

    @Override
    public PostResponse incrementComments(Long postId) {
        Post post = getActivePost(postId);
        post.setCommentsCount(post.getCommentsCount() + 1);
        return toResponse(postRepository.save(post));
    }

    @Override
    public PostResponse changeVisibility(String authorizationHeader, Long postId, PostVisibility visibility) {
        Post post = getOwnedPost(authorizationHeader, postId);
        post.setVisibility(visibility);
        return toResponse(postRepository.save(post));
    }

    @Override
    @Transactional(readOnly = true)
    public long getPostCount() {
        return postRepository.countByIsDeletedFalse();
    }

    private Post getActivePost(Long postId) {
        return postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    private Post getOwnedPost(String authorizationHeader, Long postId) {
        Post post = getActivePost(postId);
        Long requesterId = resolveCurrentUserId(authorizationHeader);
        if (!Objects.equals(post.getAuthorId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only modify your own post");
        }
        return post;
    }

    private boolean isVisibleToGeneralSearch(Post post) {
        return post.getVisibility() == PostVisibility.PUBLIC || post.getVisibility() == PostVisibility.FOLLOWERS_ONLY;
    }

    private boolean canViewPost(Post post, Long requesterId) {
        if (Objects.equals(post.getAuthorId(), requesterId)) {
            return true;
        }
        if (post.getVisibility() == PostVisibility.PUBLIC) {
            return true;
        }
        if (post.getVisibility() == PostVisibility.PRIVATE) {
            return false;
        }
        return fetchFollowingIds(post.getAuthorId()).contains(requesterId);
    }

    private List<String> normalizeMediaUrls(List<String> mediaUrls) {
        if (mediaUrls == null) {
            return new ArrayList<>();
        }
        return mediaUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private PostType inferType(List<String> mediaUrls) {
        return mediaUrls == null || mediaUrls.isEmpty() ? PostType.TEXT : PostType.MEDIA;
    }

    private PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getPostId(),
                post.getAuthorId(),
                post.getContent(),
                List.copyOf(post.getMediaUrls()),
                post.getPostType(),
                post.getVisibility(),
                post.getLikesCount(),
                post.getCommentsCount(),
                post.getSharesCount(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.isDeleted()
        );
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
                    org.springframework.http.HttpMethod.GET,
                    request,
                    AuthProfileResponse.class
            ).getBody();

            if (profile == null || profile.userId() == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
            }
            return profile.userId();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
    }

    private List<Long> fetchFollowingIds(Long userId) {
        try {
            String url = followServiceUrl + followServicePath.replace("{userId}", String.valueOf(userId));
            Long[] result = restTemplate.getForObject(url, Long[].class);
            if (result == null) {
                return List.of();
            }
            return List.of(result);
        } catch (RestClientException ex) {
            return List.of();
        }
    }
}
