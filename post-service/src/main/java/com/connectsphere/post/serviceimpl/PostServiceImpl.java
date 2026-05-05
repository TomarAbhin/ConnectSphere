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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.HttpClientErrorException;
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
    private final String searchServiceUrl;
    private final String notificationServiceUrl;

    public PostServiceImpl(
            PostRepository postRepository,
            RestTemplate restTemplate,
            @Value("${app.services.follow-service.url:http://localhost:8085}") String followServiceUrl,
            @Value("${app.services.follow-service.following-path:/follows/{userId}/following-ids}") String followServicePath,
            @Value("${app.services.auth-service.url:http://localhost:8081}") String authServiceUrl,
            @Value("${app.services.search-service.url:http://localhost:8088}") String searchServiceUrl
            ,@Value("${app.services.notification-service.url:http://localhost:8086}") String notificationServiceUrl
    ) {
        this.postRepository = postRepository;
        this.restTemplate = restTemplate;
        this.followServiceUrl = followServiceUrl;
        this.followServicePath = followServicePath;
        this.authServiceUrl = authServiceUrl;
        this.searchServiceUrl = searchServiceUrl;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @Override
    public void backfillAuthorSnapshots(String authorizationHeader) {
        ensureAdmin(authorizationHeader);
        List<com.connectsphere.post.entity.Post> posts = postRepository.findWithMissingAuthorSnapshot();
        if (posts.isEmpty()) return;
        for (com.connectsphere.post.entity.Post p : posts) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> user = restTemplate.getForObject(authServiceUrl + "/auth/users/" + p.getAuthorId(), java.util.Map.class);
                if (user != null) {
                    Object uname = user.get("username");
                    Object fname = user.get("fullName");
                    Object pic = user.get("profilePicUrl");
                    if (uname instanceof String) p.setAuthorUsername(((String) uname).trim());
                    if (fname instanceof String) p.setAuthorFullName(((String) fname).trim());
                    if (pic instanceof String) p.setAuthorProfilePicUrl(((String) pic).trim());
                } else {
                    p.setAuthorUsername("User " + p.getAuthorId());
                }
            } catch (Exception ex) {
                p.setAuthorUsername("User " + p.getAuthorId());
            }
        }
        postRepository.saveAll(posts);
    }

    @Override
    public PostResponse createPost(String authorizationHeader, CreatePostRequest request) {
        Long authorId = resolveCurrentUserId(authorizationHeader);
        Post post = new Post();
        post.setAuthorId(authorId);
        // store author snapshot to keep posts readable if the user is removed
        try {
            AuthProfileResponse profile = resolveCurrentProfile(authorizationHeader);
            if (profile != null) {
                post.setAuthorUsername(profile.username());
                post.setAuthorFullName(profile.fullName());
                post.setAuthorProfilePicUrl(profile.profilePicUrl());
            }
        } catch (Exception ignored) {
        }
        post.setContent(request.content().trim());
        post.setMediaUrls(normalizeMediaUrls(request.mediaUrls()));
        post.setPostType(request.postType() == null ? inferType(post.getMediaUrls()) : request.postType());
        post.setVisibility(request.visibility() == null ? PostVisibility.PUBLIC : request.visibility());
        Post saved = postRepository.save(post);
        // detect mentions in content and notify mentioned users
        try {
            if (saved.getContent() != null && !saved.getContent().isBlank()) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("@([A-Za-z0-9_\\-]+)").matcher(saved.getContent());
                java.util.Set<String> mentioned = new java.util.LinkedHashSet<>();
                while (m.find()) mentioned.add(m.group(1));
                for (String username : mentioned) {
                    try {
                        String url = authServiceUrl + "/auth/search?query=" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8);
                        java.util.Map[] results = restTemplate.getForObject(url, java.util.Map[].class);
                        if (results != null && results.length > 0) {
                            Object uid = results[0].get("userId");
                            if (uid instanceof Number) {
                                Long recipientId = ((Number) uid).longValue();
                                if (!recipientId.equals(authorId)) {
                                    java.util.Map<String, Object> payload = new java.util.HashMap<>();
                                    payload.put("recipientId", recipientId);
                                    payload.put("actorId", authorId);
                                    payload.put("actionType", "MENTION");
                                    payload.put("targetType", "POST");
                                    payload.put("targetId", saved.getPostId());
                                    payload.put("message", "You were mentioned in a post");
                                    payload.put("deepLink", "/post/" + saved.getPostId());
                                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                                    headers.set("Authorization", authorizationHeader);
                                    org.springframework.http.HttpEntity<java.util.Map<String, Object>> req = new org.springframework.http.HttpEntity<>(payload, headers);
                                    restTemplate.postForObject(notificationServiceUrl + "/notifications", req, java.util.Map.class);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public PostResponse getPostById(String authorizationHeader, Long postId) {
        Long requesterId = resolveCurrentUserIdOrNull(authorizationHeader);
        Post post = getActivePost(postId);
        post = refreshAuthorSnapshotOrDelete(post);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
        if (!canViewPost(post, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this post");
        }
        return toResponse(post);
    }

    @Override
    @Transactional
    public List<PostResponse> getPostsByUser(String authorizationHeader, Long userId) {
        Long requesterId = resolveCurrentUserIdOrNull(authorizationHeader);
        return postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::refreshAuthorSnapshotOrDelete)
                .filter(Objects::nonNull)
                .filter(post -> canViewPost(post, requesterId))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<PostResponse> getFeedForUser(String authorizationHeader) {
        Long requesterId = resolveCurrentUserIdOrNull(authorizationHeader);
        if (requesterId == null) {
            return postRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(PostVisibility.PUBLIC)
                    .stream()
                    .map(this::refreshAuthorSnapshotOrDelete)
                    .filter(Objects::nonNull)
                    .map(this::toResponse)
                    .toList();
        }

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
                .map(this::refreshAuthorSnapshotOrDelete)
                .filter(Objects::nonNull)
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
        cleanupSearchIndex(postId);
    }

    @Override
    public void deletePostAsAdmin(String authorizationHeader, Long postId) {
        ensureAdmin(authorizationHeader);
        Post post = getActivePost(postId);
        post.setDeleted(true);
        postRepository.save(post);
        cleanupSearchIndex(postId);
    }

    @Override
    public void deletePostsByAuthor(String authorizationHeader, Long authorId) {
        if (authorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Author id is required");
        }
        Long requesterId = resolveCurrentUserIdOrNull(authorizationHeader);
        boolean requesterIsAdmin = false;
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", authorizationHeader);
                HttpEntity<Void> request = new HttpEntity<>(headers);
                com.connectsphere.post.dto.AuthProfileResponse profile = restTemplate.exchange(
                        authServiceUrl + "/auth/profile",
                        HttpMethod.GET,
                        request,
                        com.connectsphere.post.dto.AuthProfileResponse.class
                ).getBody();
                requesterIsAdmin = profile != null && profile.role() != null && "ADMIN".equalsIgnoreCase(profile.role());
            } catch (Exception ex) {
                requesterIsAdmin = false;
            }
        }
        if (!requesterIsAdmin && !Objects.equals(requesterId, authorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
        }
        List<Post> posts = postRepository.findByAuthorId(authorId);
        if (posts.isEmpty()) {
            return;
        }
        postRepository.deleteAll(posts);
        posts.forEach(post -> cleanupSearchIndex(post.getPostId()));
    }

    @Override
    @Transactional
    public List<PostResponse> searchPosts(String authorizationHeader, String query) {
        Long requesterId = resolveCurrentUserIdOrNull(authorizationHeader);
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) {
            return postRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(PostVisibility.PUBLIC)
                    .stream()
                    .map(this::refreshAuthorSnapshotOrDelete)
                    .filter(Objects::nonNull)
                    .filter(post -> canViewPost(post, requesterId))
                    .map(this::toResponse)
                    .toList();
        }
        return postRepository.searchByContent(safeQuery)
                .stream()
                .map(this::refreshAuthorSnapshotOrDelete)
                .filter(Objects::nonNull)
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
    public PostResponse decrementComments(Long postId) {
        Post post = getActivePost(postId);
        post.setCommentsCount(Math.max(0, post.getCommentsCount() - 1));
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
        AuthProfileResponse profile = resolveCurrentProfile(authorizationHeader);
        if (!Objects.equals(post.getAuthorId(), profile.userId()) && !isAdmin(profile)) {
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
        if (requesterId == null) {
            return false;
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
        // try to fetch live author info; fall back to stored snapshot
        String authorUsername = post.getAuthorUsername();
        String authorFullName = post.getAuthorFullName();
        String authorProfilePicUrl = post.getAuthorProfilePicUrl();
        // Avoid making network calls here to prevent noisy 404s when auth users were removed.
        // Use stored snapshot when available; otherwise fall back to a simple placeholder.
        if (authorUsername == null || authorUsername.isBlank()) {
            authorUsername = "User " + post.getAuthorId();
        }
        if (authorFullName == null || authorFullName.isBlank()) {
            authorFullName = null; // leave blank if not available
        }
        if (authorProfilePicUrl == null || authorProfilePicUrl.isBlank()) {
            authorProfilePicUrl = null;
        }

        return new PostResponse(
                post.getPostId(),
                post.getAuthorId(),
                authorUsername,
                authorFullName,
                authorProfilePicUrl,
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

    private Post refreshAuthorSnapshotOrDelete(Post post) {
        if (post == null) {
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> user = restTemplate.getForObject(
                    authServiceUrl + "/auth/users/" + post.getAuthorId(),
                    java.util.Map.class
            );
            if (user == null) {
                return post;
            }

            boolean updated = false;

            String username = toTrimmedString(user.get("username"));
            if (username != null && !Objects.equals(post.getAuthorUsername(), username)) {
                post.setAuthorUsername(username);
                updated = true;
            }

            String fullName = toTrimmedString(user.get("fullName"));
            if (fullName != null && !Objects.equals(post.getAuthorFullName(), fullName)) {
                post.setAuthorFullName(fullName);
                updated = true;
            }

            String profilePicUrl = toTrimmedString(user.get("profilePicUrl"));
            if (profilePicUrl != null && !Objects.equals(post.getAuthorProfilePicUrl(), profilePicUrl)) {
                post.setAuthorProfilePicUrl(profilePicUrl);
                updated = true;
            }

            if ((post.getAuthorUsername() == null || post.getAuthorUsername().isBlank()) && username == null) {
                post.setAuthorUsername("User " + post.getAuthorId());
                updated = true;
            }

            return updated ? postRepository.save(post) : post;
        } catch (HttpClientErrorException.NotFound ex) {
            postRepository.delete(post);
            cleanupSearchIndex(post.getPostId());
            return null;
        } catch (RestClientException ex) {
            return post;
        }
    }

    private String toTrimmedString(Object value) {
        if (!(value instanceof String stringValue)) {
            return null;
        }
        String trimmed = stringValue.trim();
        return trimmed.isBlank() ? null : trimmed;
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
            if (profile.role() != null && "GUEST".equalsIgnoreCase(profile.role())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest accounts have read-only access");
            }
            return profile.userId();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
    }

    private void ensureAdmin(String authorizationHeader) {
        try {
            AuthProfileResponse profile = resolveCurrentProfile(authorizationHeader);
            if (!isAdmin(profile)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
        }
    }

    private AuthProfileResponse resolveCurrentProfile(String authorizationHeader) {
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
            if (profile == null || profile.userId() == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
            }
            if (profile.role() != null && "GUEST".equalsIgnoreCase(profile.role())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest accounts have read-only access");
            }
            return profile;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
    }

    private boolean isAdmin(AuthProfileResponse profile) {
        return profile != null && profile.role() != null && "ADMIN".equalsIgnoreCase(profile.role());
    }

    private void cleanupSearchIndex(Long postId) {
        if (postId == null || searchServiceUrl == null || searchServiceUrl.isBlank()) {
            return;
        }
        try {
            restTemplate.exchange(
                    searchServiceUrl + "/search/index/" + postId,
                    HttpMethod.DELETE,
                    HttpEntity.EMPTY,
                    Void.class
            );
        } catch (RestClientException ignored) {
        }
    }

    private Long resolveCurrentUserIdOrNull(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        try {
            return resolveCurrentUserId(authorizationHeader);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return null;
            }
            throw ex;
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
