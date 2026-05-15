package com.connectsphere.search.serviceimpl;

import com.connectsphere.search.dto.AuthProfileResponse;
import com.connectsphere.search.dto.HashtagResponse;
import com.connectsphere.search.dto.IndexPostRequest;
import com.connectsphere.search.dto.UserRole;
import com.connectsphere.search.dto.UserSearchResponse;
import com.connectsphere.search.entity.Hashtag;
import com.connectsphere.search.entity.IndexedPost;
import com.connectsphere.search.entity.PostHashtag;
import com.connectsphere.search.entity.PostVisibility;
import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import com.connectsphere.search.repository.SearchRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private SearchRepository searchRepository;

    @Mock
    private HashtagRepository hashtagRepository;

    @Mock
    private PostHashtagRepository postHashtagRepository;

    @Mock
    private RestTemplate restTemplate;

    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchServiceImpl(
                searchRepository,
                hashtagRepository,
                postHashtagRepository,
                restTemplate,
                "http://auth",
                "http://follow"
        );
    }

    @Test
    void upsertHashtagForAdminNormalizesAndSavesTag() {
        stubProfile(profile(1L, "admin", "ADMIN"));
        when(hashtagRepository.findByTagIgnoreCase("helloworld")).thenReturn(Optional.empty());
        when(hashtagRepository.save(any(Hashtag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HashtagResponse response = searchService.upsertHashtagForAdmin("Bearer token", "  #HelloWorld  ");

        assertNotNull(response);
        assertEquals("helloworld", response.tag());
        assertEquals(1L, response.postCount());

        ArgumentCaptor<Hashtag> hashtagCaptor = ArgumentCaptor.forClass(Hashtag.class);
        org.mockito.Mockito.verify(hashtagRepository).save(hashtagCaptor.capture());
        assertEquals("helloworld", hashtagCaptor.getValue().getTag());
        assertEquals(1L, hashtagCaptor.getValue().getPostCount());
    }

    @Test
    void indexPostExtractsHashtagsAndReplacesExistingIndex() {
        when(postHashtagRepository.findByPost_PostId(5L)).thenReturn(List.of());
        when(searchRepository.save(any(IndexedPost.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hashtagRepository.findByTagIgnoreCase("java")).thenReturn(Optional.empty());
        when(hashtagRepository.findByTagIgnoreCase("spring")).thenReturn(Optional.of(hashtag("spring", 2L)));
        when(hashtagRepository.save(any(Hashtag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postHashtagRepository.save(any(PostHashtag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        searchService.indexPost(new IndexPostRequest(5L, 7L, "Learning #Java and #Spring", List.of("#java"), null, false));

        ArgumentCaptor<IndexedPost> postCaptor = ArgumentCaptor.forClass(IndexedPost.class);
        verify(searchRepository).save(postCaptor.capture());
        assertEquals(PostVisibility.PUBLIC, postCaptor.getValue().getVisibility());
        verify(postHashtagRepository, org.mockito.Mockito.times(2)).save(any(PostHashtag.class));
    }

    @Test
    void indexPostRejectsMissingPostIdAndAdminIndexRequiresAdmin() {
        ResponseStatusException missingPost = assertThrows(
                ResponseStatusException.class,
                () -> searchService.indexPost(new IndexPostRequest(null, 7L, "content", List.of(), PostVisibility.PUBLIC, false))
        );
        assertEquals(HttpStatus.BAD_REQUEST, missingPost.getStatusCode());

        stubProfile(profile(2L, "user", "USER"));
        ResponseStatusException forbidden = assertThrows(
                ResponseStatusException.class,
                () -> searchService.indexPostForAdmin("Bearer token", new IndexPostRequest(5L, 7L, "content", List.of(), PostVisibility.PUBLIC, false))
        );
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    }

    @Test
    void deletedIndexRequestRemovesPostIndexAndDecrementsTags() {
        Hashtag hashtag = hashtag("java", 1L);
        IndexedPost indexedPost = indexedPost(5L, 7L, PostVisibility.PUBLIC);
        PostHashtag mapping = mapping(indexedPost, hashtag);
        when(postHashtagRepository.findByPost_PostId(5L)).thenReturn(List.of(mapping));
        when(searchRepository.findByPostId(5L)).thenReturn(Optional.of(indexedPost));

        searchService.indexPost(new IndexPostRequest(5L, 7L, "gone", List.of(), PostVisibility.PUBLIC, true));

        assertEquals(0L, hashtag.getPostCount());
        verify(hashtagRepository).delete(hashtag);
        verify(postHashtagRepository).deleteByPost_PostId(5L);
        verify(searchRepository).delete(indexedPost);
    }

    @Test
    void removePostIndexSavesRemainingHashtagCountsAndIgnoresNullIds() {
        Hashtag hashtag = hashtag("java", 3L);
        IndexedPost indexedPost = indexedPost(9L, 7L, PostVisibility.PUBLIC);
        when(postHashtagRepository.findByPost_PostId(9L)).thenReturn(List.of(mapping(indexedPost, hashtag)));
        when(searchRepository.findByPostId(9L)).thenReturn(Optional.empty());

        searchService.removePostIndex(null);
        searchService.removePostIndex(9L);

        assertEquals(2L, hashtag.getPostCount());
        verify(hashtagRepository).save(hashtag);
        verify(postHashtagRepository).deleteByPost_PostId(9L);
    }

    @Test
    void searchPostsCombinesContentAndHashtagResultsWithVisibilityRules() {
        IndexedPost publicPost = indexedPost(1L, 2L, PostVisibility.PUBLIC);
        IndexedPost privatePost = indexedPost(2L, 3L, PostVisibility.PRIVATE);
        IndexedPost followedPost = indexedPost(3L, 4L, PostVisibility.FOLLOWERS_ONLY);
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile(9L, "viewer", "USER"), HttpStatus.OK));
        when(restTemplate.getForObject("http://follow/follows/9/following", com.connectsphere.search.dto.FollowingResponse[].class))
                .thenReturn(new com.connectsphere.search.dto.FollowingResponse[]{
                        new com.connectsphere.search.dto.FollowingResponse(4L, "followed", "Followed User", null, "USER")
                });
        when(searchRepository.findByDeletedFalseAndContentContainingIgnoreCaseOrderByCreatedAtDesc("java"))
                .thenReturn(List.of(publicPost, privatePost));
        when(postHashtagRepository.findPostIdsByHashtag("java")).thenReturn(List.of(3L));
        when(searchRepository.findByPostId(3L)).thenReturn(Optional.of(followedPost));
        when(hashtagRepository.findByTagContainingIgnoreCaseOrderByTagAsc("java")).thenReturn(List.of());
        when(postHashtagRepository.findByPost_PostId(any())).thenReturn(List.of());

        var results = searchService.searchPosts("Bearer token", "java");

        assertEquals(List.of(3L, 1L), results.stream().map(response -> response.postId()).toList());
    }

    @Test
    void blankSearchReturnsVisiblePostsAndAdminSearchIncludesPrivatePosts() {
        IndexedPost publicPost = indexedPost(1L, 2L, PostVisibility.PUBLIC);
        IndexedPost privatePost = indexedPost(2L, 3L, PostVisibility.PRIVATE);
        when(searchRepository.findByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(privatePost, publicPost));
        when(postHashtagRepository.findByPost_PostId(any())).thenReturn(List.of());

        assertEquals(List.of(1L), searchService.searchPosts(null, " ").stream().map(response -> response.postId()).toList());

        stubProfile(profile(9L, "admin", "ADMIN"));
        assertEquals(List.of(2L, 1L), searchService.searchPostsForAdmin("Bearer token", " ").stream().map(response -> response.postId()).toList());
    }

    @Test
    void adminSearchCombinesContentExactHashtagAndRelatedHashtagMatches() {
        stubProfile(profile(9L, "admin", "ADMIN"));
        IndexedPost contentMatch = indexedPost(1L, 2L, PostVisibility.PUBLIC);
        IndexedPost exactHashtagMatch = indexedPost(2L, 3L, PostVisibility.PRIVATE);
        IndexedPost relatedHashtagMatch = indexedPost(3L, 4L, PostVisibility.FOLLOWERS_ONLY);
        when(searchRepository.findByDeletedFalseAndContentContainingIgnoreCaseOrderByCreatedAtDesc("java"))
                .thenReturn(List.of(contentMatch));
        when(postHashtagRepository.findPostIdsByHashtag("java")).thenReturn(List.of(2L));
        when(searchRepository.findByPostId(2L)).thenReturn(Optional.of(exactHashtagMatch));
        when(hashtagRepository.findByTagContainingIgnoreCaseOrderByTagAsc("java"))
                .thenReturn(List.of(hashtag("springjava", 1L)));
        when(postHashtagRepository.findPostIdsByHashtag("springjava")).thenReturn(List.of(3L));
        when(searchRepository.findByPostId(3L)).thenReturn(Optional.of(relatedHashtagMatch));
        when(postHashtagRepository.findByPost_PostId(any())).thenReturn(List.of());

        var results = searchService.searchPostsForAdmin("Bearer token", "java");

        assertEquals(List.of(3L, 2L, 1L), results.stream().map(response -> response.postId()).toList());
    }

    @Test
    void searchUsersPassesRoleAndReturnsEmptyForNullBody() {
        when(restTemplate.exchange(
                eq("http://auth/auth/search?query=ann&role=ADMIN"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserSearchResponse[].class)
        )).thenReturn(new ResponseEntity<>(new UserSearchResponse[]{
                new UserSearchResponse(1L, "ann", "Ann", null, UserRole.ADMIN)
        }, HttpStatus.OK));

        var users = searchService.searchUsers("Bearer token", " ann ", UserRole.ADMIN);

        assertEquals(1, users.size());
        assertEquals("ann", users.get(0).username());
    }

    @Test
    void searchUsersHandlesNullBodyAndGatewayFailures() {
        when(restTemplate.exchange(
                eq("http://auth/auth/search?query="),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserSearchResponse[].class)
        )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertEquals(List.of(), searchService.searchUsers(null, null, null));

        when(restTemplate.exchange(
                eq("http://auth/auth/search?query=fail"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserSearchResponse[].class)
        )).thenThrow(new org.springframework.web.client.RestClientException("down"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> searchService.searchUsers(null, "fail", null)
        );
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void simpleHashtagQueriesReturnExpectedResults() {
        Hashtag java = hashtag("java", 4L);
        IndexedPost post = indexedPost(8L, 2L, PostVisibility.PUBLIC);
        PostHashtag mapping = mapping(post, java);
        when(postHashtagRepository.findByPost_PostId(8L)).thenReturn(List.of(mapping));
        when(hashtagRepository.findTop20ByOrderByPostCountDescLastUsedAtDesc()).thenReturn(List.of(java));
        when(postHashtagRepository.findByHashtagTagOrderByCreatedAtDesc("java")).thenReturn(List.of(mapping));
        when(postHashtagRepository.countPostsByHashtag("java")).thenReturn(4L);

        assertEquals("java", searchService.getHashtagsForPost(8L).get(0).tag());
        assertEquals(4L, searchService.getTrendingHashtags().get(0).postCount());
        assertEquals(1, searchService.getPostsByHashtag(null, "#Java").size());
        assertEquals(4L, searchService.getHashtagCount(" java "));
    }

    @Test
    void hashtagSearchFallsBackToTrendingAndBlankCountsAreZero() {
        Hashtag java = hashtag("java", 4L);
        when(hashtagRepository.findTop20ByOrderByPostCountDescLastUsedAtDesc()).thenReturn(List.of(java));
        when(hashtagRepository.findByTagContainingIgnoreCaseOrderByTagAsc("ja")).thenReturn(List.of(java));

        assertEquals("java", searchService.searchHashtags(" ").get(0).tag());
        assertEquals("java", searchService.searchHashtags(" ja ").get(0).tag());
        assertEquals(List.of(), searchService.getPostsByHashtag(null, " "));
        assertEquals(0L, searchService.getHashtagCount(" "));
    }

    @Test
    void getIndexedPostThrowsWhenMissing() {
        when(searchRepository.findByPostId(77L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> searchService.getIndexedPost(77L));
    }

    private void stubProfile(AuthProfileResponse profile) {
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));
    }

    private AuthProfileResponse profile(Long userId, String username, String role) {
        return new AuthProfileResponse(
                userId,
                username,
                username + "@example.com",
                "Full " + username,
                null,
                null,
                role,
                "LOCAL",
                true,
                Instant.parse("2026-05-05T00:00:00Z")
        );
    }

    private Hashtag hashtag(String tag, long postCount) {
        Hashtag hashtag = new Hashtag(tag);
        hashtag.setPostCount(postCount);
        hashtag.setLastUsedAt(Instant.parse("2026-05-05T00:00:00Z"));
        return hashtag;
    }

    private IndexedPost indexedPost(Long postId, Long authorId, PostVisibility visibility) {
        IndexedPost post = new IndexedPost();
        post.setPostId(postId);
        post.setAuthorId(authorId);
        post.setContent("Java content " + postId);
        post.setVisibility(visibility);
        post.setDeleted(false);
        post.setCreatedAt(Instant.parse("2026-05-05T00:00:0" + Math.min(postId, 9) + "Z"));
        return post;
    }

    private PostHashtag mapping(IndexedPost post, Hashtag hashtag) {
        PostHashtag mapping = new PostHashtag();
        mapping.setPost(post);
        mapping.setHashtag(hashtag);
        return mapping;
    }
}
