package com.connectsphere.search.serviceimpl;

import com.connectsphere.search.dto.HashtagResponse;
import com.connectsphere.search.dto.AuthProfileResponse;
import com.connectsphere.search.dto.IndexPostRequest;
import com.connectsphere.search.dto.PostSearchResponse;
import com.connectsphere.search.dto.UserRole;
import com.connectsphere.search.dto.UserSearchResponse;
import com.connectsphere.search.entity.Hashtag;
import com.connectsphere.search.entity.IndexedPost;
import com.connectsphere.search.entity.PostHashtag;
import com.connectsphere.search.entity.PostVisibility;
import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import com.connectsphere.search.repository.SearchRepository;
import com.connectsphere.search.service.SearchService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
public class SearchServiceImpl implements SearchService {

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("(?<!\\w)#([A-Za-z0-9_]+)");

    private final SearchRepository searchRepository;
    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final RestTemplate restTemplate;
    private final String authServiceUrl;
    private final String followServiceUrl;

    public SearchServiceImpl(
            SearchRepository searchRepository,
            HashtagRepository hashtagRepository,
            PostHashtagRepository postHashtagRepository,
            RestTemplate restTemplate,
            @Value("${app.services.auth-service.url:http://localhost:8081}") String authServiceUrl,
            @Value("${app.services.follow-service.url:http://localhost:8085}") String followServiceUrl
    ) {
        this.searchRepository = searchRepository;
        this.hashtagRepository = hashtagRepository;
        this.postHashtagRepository = postHashtagRepository;
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
        this.followServiceUrl = followServiceUrl;
    }

    @Override
    public void indexPost(IndexPostRequest request) {
        if (request == null || request.postId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post id is required");
        }
        if (request.deleted() != null && request.deleted()) {
            removePostIndex(request.postId());
            return;
        }

        removePostIndex(request.postId());

        IndexedPost post = new IndexedPost();
        post.setPostId(request.postId());
        post.setAuthorId(request.authorId());
        post.setContent(request.content() == null ? "" : request.content().trim());
        post.setVisibility(request.visibility() == null ? PostVisibility.PUBLIC : request.visibility());
        post.setDeleted(Boolean.TRUE.equals(request.deleted()));
        IndexedPost savedPost = searchRepository.save(post);

        Set<String> tags = extractHashtags(request.content());
        if (request.hashtags() != null) {
            tags.addAll(request.hashtags().stream()
                    .filter(Objects::nonNull)
                    .map(this::normalizeTag)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        for (String tag : tags) {
            Hashtag hashtag = upsertHashtag(tag);
            PostHashtag mapping = new PostHashtag();
            mapping.setPost(savedPost);
            mapping.setHashtag(hashtag);
            postHashtagRepository.save(mapping);
        }
    }

    @Override
    public void indexPostForAdmin(String authorizationHeader, IndexPostRequest request) {
        ensureAdmin(authorizationHeader);
        indexPost(request);
    }

    @Override
    public HashtagResponse upsertHashtagForAdmin(String authorizationHeader, String tag) {
        ensureAdmin(authorizationHeader);
        String normalizedTag = normalizeTag(tag);
        if (normalizedTag.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag is required");
        }
        Hashtag hashtag = upsertHashtag(normalizedTag);
        return toHashtagResponse(hashtag);
    }

    @Override
    public void removePostIndex(Long postId) {
        if (postId == null) {
            return;
        }

        List<PostHashtag> mappings = postHashtagRepository.findByPost_PostId(postId);
        for (PostHashtag mapping : mappings) {
            Hashtag hashtag = mapping.getHashtag();
            if (hashtag != null) {
                hashtag.setPostCount(Math.max(0, hashtag.getPostCount() - 1));
                hashtag.setLastUsedAt(Instant.now());
                if (hashtag.getPostCount() <= 0) {
                    hashtagRepository.delete(hashtag);
                } else {
                    hashtagRepository.save(hashtag);
                }
            }
        }

        postHashtagRepository.deleteByPost_PostId(postId);
        searchRepository.findByPostId(postId).ifPresent(searchRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostSearchResponse> searchPosts(String authorizationHeader, String query) {
        Long requesterId = resolveRequesterId(authorizationHeader);
        String safeQuery = query == null ? "" : query.trim();

        List<IndexedPost> posts;
        if (safeQuery.isBlank()) {
            posts = searchRepository.findByDeletedFalseOrderByCreatedAtDesc();
        } else {
            Map<Long, IndexedPost> combined = new LinkedHashMap<>();
            searchRepository.findByDeletedFalseAndContentContainingIgnoreCaseOrderByCreatedAtDesc(safeQuery)
                    .forEach(post -> combined.putIfAbsent(post.getPostId(), post));

            String normalizedTag = normalizeTag(safeQuery);
            if (!normalizedTag.isBlank()) {
                postHashtagRepository.findPostIdsByHashtag(normalizedTag)
                        .stream()
                        .distinct()
                        .map(searchRepository::findByPostId)
                        .flatMap(java.util.Optional::stream)
                        .filter(post -> !post.isDeleted())
                        .forEach(post -> combined.putIfAbsent(post.getPostId(), post));
            }

            hashtagRepository.findByTagContainingIgnoreCaseOrderByTagAsc(safeQuery)
                    .stream()
                    .map(Hashtag::getTag)
                    .forEach(tag -> postHashtagRepository.findPostIdsByHashtag(tag)
                            .stream()
                            .distinct()
                            .map(searchRepository::findByPostId)
                            .flatMap(java.util.Optional::stream)
                            .filter(post -> !post.isDeleted())
                            .forEach(post -> combined.putIfAbsent(post.getPostId(), post)));

            posts = new ArrayList<>(combined.values());
        }

        return posts.stream()
                .filter(post -> canViewPost(post, requesterId))
                .sorted(Comparator.comparing(IndexedPost::getCreatedAt).reversed())
                .map(this::toPostResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostSearchResponse> searchPostsForAdmin(String authorizationHeader, String query) {
        ensureAdmin(authorizationHeader);
        String safeQuery = query == null ? "" : query.trim();

        List<IndexedPost> posts;
        if (safeQuery.isBlank()) {
            posts = searchRepository.findByDeletedFalseOrderByCreatedAtDesc();
        } else {
            Map<Long, IndexedPost> combined = new LinkedHashMap<>();
            searchRepository.findByDeletedFalseAndContentContainingIgnoreCaseOrderByCreatedAtDesc(safeQuery)
                    .forEach(post -> combined.putIfAbsent(post.getPostId(), post));

            String normalizedTag = normalizeTag(safeQuery);
            if (!normalizedTag.isBlank()) {
                postHashtagRepository.findPostIdsByHashtag(normalizedTag)
                        .stream()
                        .distinct()
                        .map(searchRepository::findByPostId)
                        .flatMap(java.util.Optional::stream)
                        .filter(post -> !post.isDeleted())
                        .forEach(post -> combined.putIfAbsent(post.getPostId(), post));
            }

            hashtagRepository.findByTagContainingIgnoreCaseOrderByTagAsc(safeQuery)
                    .stream()
                    .map(Hashtag::getTag)
                    .forEach(tag -> postHashtagRepository.findPostIdsByHashtag(tag)
                            .stream()
                            .distinct()
                            .map(searchRepository::findByPostId)
                            .flatMap(java.util.Optional::stream)
                            .filter(post -> !post.isDeleted())
                            .forEach(post -> combined.putIfAbsent(post.getPostId(), post)));

            posts = new ArrayList<>(combined.values());
        }

        return posts.stream()
                .sorted(Comparator.comparing(IndexedPost::getCreatedAt).reversed())
                .map(this::toPostResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(String authorizationHeader, String query, UserRole role) {
        try {
            StringBuilder url = new StringBuilder(authServiceUrl)
                    .append("/auth/search?query=")
                    .append(query == null ? "" : query.trim());
            if (role != null) {
                url.append("&role=").append(role.name());
            }

            HttpHeaders headers = new HttpHeaders();
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                headers.set("Authorization", authorizationHeader);
            }
            HttpEntity<Void> request = new HttpEntity<>(headers);
            UserSearchResponse[] response = restTemplate.exchange(
                    url.toString(),
                    HttpMethod.GET,
                    request,
                    UserSearchResponse[].class
            ).getBody();
            return response == null ? List.of() : List.of(response);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to search users");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<HashtagResponse> getHashtagsForPost(Long postId) {
        return postHashtagRepository.findByPost_PostId(postId).stream()
                .map(PostHashtag::getHashtag)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Hashtag::getTag))
                .map(this::toHashtagResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HashtagResponse> getTrendingHashtags() {
        return hashtagRepository.findTop20ByOrderByPostCountDescLastUsedAtDesc().stream()
                .map(this::toHashtagResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostSearchResponse> getPostsByHashtag(String authorizationHeader, String tag) {
        String normalizedTag = normalizeTag(tag);
        if (normalizedTag.isBlank()) {
            return List.of();
        }

        Long requesterId = resolveRequesterId(authorizationHeader);

        return postHashtagRepository.findByHashtagTagOrderByCreatedAtDesc(normalizedTag).stream()
                .map(PostHashtag::getPost)
                .filter(Objects::nonNull)
                .filter(post -> !post.isDeleted())
                .filter(post -> canViewPost(post, requesterId))
                .distinct()
                .map(this::toPostResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HashtagResponse> searchHashtags(String query) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) {
            return getTrendingHashtags();
        }
        return hashtagRepository.findByTagContainingIgnoreCaseOrderByTagAsc(safeQuery).stream()
                .map(this::toHashtagResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getHashtagCount(String tag) {
        String normalizedTag = normalizeTag(tag);
        if (normalizedTag.isBlank()) {
            return 0L;
        }
        return postHashtagRepository.countPostsByHashtag(normalizedTag);
    }

    public IndexedPost getIndexedPost(Long postId) {
        return searchRepository.findByPostId(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not indexed"));
    }

    private Hashtag upsertHashtag(String tag) {
        Hashtag hashtag = hashtagRepository.findByTagIgnoreCase(tag)
                .orElseGet(() -> new Hashtag(tag));
        hashtag.setTag(tag);
        hashtag.setPostCount(hashtag.getPostCount() + 1);
        hashtag.setLastUsedAt(Instant.now());
        return hashtagRepository.save(hashtag);
    }

    private Set<String> extractHashtags(String content) {
        Set<String> tags = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return tags;
        }
        Matcher matcher = HASHTAG_PATTERN.matcher(content);
        while (matcher.find()) {
            tags.add(normalizeTag(matcher.group(1)));
        }
        return tags;
    }

    private String normalizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        String normalized = tag.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase();
    }

    private boolean canViewPost(IndexedPost post, Long requesterId) {
        if (post == null || post.isDeleted()) {
            return false;
        }
        if (requesterId != null && Objects.equals(post.getAuthorId(), requesterId)) {
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
        return fetchFollowingIds(requesterId).contains(post.getAuthorId());
    }

    private Long resolveRequesterId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
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
            return profile.userId();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
    }

    private void ensureAdmin(String authorizationHeader) {
        Long requesterId = resolveRequesterId(authorizationHeader);
        if (requesterId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
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
            if (profile == null || profile.userId() == null || !requesterId.equals(profile.userId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
            }
            if (profile.role() == null || !"ADMIN".equalsIgnoreCase(profile.role())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
        }
    }

    private Set<Long> fetchFollowingIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        try {
            com.connectsphere.search.dto.FollowingResponse[] response = restTemplate.getForObject(
                    followServiceUrl + "/follows/" + userId + "/following",
                    com.connectsphere.search.dto.FollowingResponse[].class
            );
            if (response == null) {
                return Set.of();
            }
            return java.util.Arrays.stream(response)
                    .filter(Objects::nonNull)
                    .map(com.connectsphere.search.dto.FollowingResponse::userId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (RestClientException ex) {
            return Set.of();
        }
    }

    private PostSearchResponse toPostResponse(IndexedPost post) {
        List<String> hashtags = postHashtagRepository.findByPost_PostId(post.getPostId()).stream()
                .map(PostHashtag::getHashtag)
                .filter(Objects::nonNull)
                .map(Hashtag::getTag)
                .distinct()
                .toList();
        return new PostSearchResponse(
                post.getPostId(),
                post.getAuthorId(),
                post.getContent(),
                hashtags,
                post.getVisibility(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.isDeleted()
        );
    }

    private HashtagResponse toHashtagResponse(Hashtag hashtag) {
        return new HashtagResponse(
                hashtag.getHashtagId(),
                hashtag.getTag(),
                hashtag.getPostCount(),
                hashtag.getLastUsedAt(),
                hashtag.getCreatedAt()
        );
    }
}