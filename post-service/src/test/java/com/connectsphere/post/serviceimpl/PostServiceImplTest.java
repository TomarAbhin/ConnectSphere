package com.connectsphere.post.serviceimpl;

import com.connectsphere.post.dto.AuthProfileResponse;
import com.connectsphere.post.dto.CreatePostRequest;
import com.connectsphere.post.dto.UpdatePostRequest;
import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.PostVisibility;
import com.connectsphere.post.repository.PostRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private RestTemplate restTemplate;

    private PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(
                postRepository,
                restTemplate,
                "http://follow",
                "/follows/{userId}/following-ids",
                "http://auth",
                "http://search",
                "http://notification"
        );
    }

    @Test
    void updatePostAsAdminAllowsAdminToEditPost() {
        AuthProfileResponse adminProfile = profile(1L, "admin", "ADMIN");
        Post post = post(10L, 3L, PostVisibility.PUBLIC);
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(adminProfile, HttpStatus.OK));
        when(postRepository.findByPostIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePostRequest request = new UpdatePostRequest(
                "  Updated content  ",
                Arrays.asList("  https://cdn.example.com/new.jpg  ", "  ", null),
                PostType.MEDIA,
                PostVisibility.FOLLOWERS_ONLY
        );

        var response = postService.updatePostAsAdmin("Bearer token", 10L, request);

        assertNotNull(response);
        assertEquals("Updated content", response.content());
        assertEquals(PostVisibility.FOLLOWERS_ONLY, response.visibility());
        assertEquals(List.of("https://cdn.example.com/new.jpg"), response.mediaUrls());
        assertFalse(response.deleted());

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertEquals("Updated content", postCaptor.getValue().getContent());
    }

    @Test
    void createPostStoresAuthorSnapshotAndNormalizesMedia() {
        AuthProfileResponse profile = profile(7L, "writer", "USER");
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            saved.setPostId(99L);
            return saved;
        });
        when(restTemplate.getForObject(eq("http://auth/auth/search?query=friend"), eq(Map[].class)))
                .thenReturn(new Map[]{Map.of("userId", 8L)});

        var response = postService.createPost(
                "Bearer token",
                new CreatePostRequest(" Hi @friend ", Arrays.asList(" /img.png ", "", null), null, null)
        );

        assertEquals(99L, response.postId());
        assertEquals(7L, response.authorId());
        assertEquals("writer", response.authorUsername());
        assertEquals(PostType.MEDIA, response.postType());
        assertEquals(PostVisibility.PUBLIC, response.visibility());
        assertEquals(List.of("/img.png"), response.mediaUrls());
        verify(restTemplate).postForObject(eq("http://notification/notifications"), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void getPostByIdBlocksPrivatePostForOtherUser() {
        Post privatePost = post(12L, 2L, PostVisibility.PRIVATE);
        when(postRepository.findByPostIdAndIsDeletedFalse(12L)).thenReturn(Optional.of(privatePost));
        when(restTemplate.getForObject("http://auth/auth/users/2", Map.class))
                .thenReturn(Map.of("username", " owner ", "fullName", " Owner Name "));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile(9L, "viewer", "USER"), HttpStatus.OK));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> postService.getPostById("Bearer token", 12L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void feedForUserMergesFollowedOwnAndPublicPosts() {
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile(5L, "me", "USER"), HttpStatus.OK));
        when(restTemplate.getForObject("http://follow/follows/5/following-ids", Long[].class))
                .thenReturn(new Long[]{2L});
        Post followed = post(1L, 2L, PostVisibility.FOLLOWERS_ONLY);
        Post own = post(2L, 5L, PostVisibility.PRIVATE);
        Post publicPost = post(3L, 8L, PostVisibility.PUBLIC);
        when(postRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(PostVisibility.PUBLIC))
                .thenReturn(List.of(publicPost));
        when(postRepository.findByAuthorIdInAndVisibilityInAndIsDeletedFalseOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(followed));
        when(postRepository.findByAuthorIdAndVisibilityInAndIsDeletedFalseOrderByCreatedAtDesc(eq(5L), any()))
                .thenReturn(List.of(own));

        var feed = postService.getFeedForUser("Bearer token");

        assertEquals(List.of(1L, 2L, 3L), feed.stream().map(response -> response.postId()).toList());
    }

    @Test
    void ownerCanUpdateDeleteAndChangeVisibility() {
        Post post = post(22L, 4L, PostVisibility.PUBLIC);
        when(postRepository.findByPostIdAndIsDeletedFalse(22L)).thenReturn(Optional.of(post));
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile(4L, "owner", "USER"), HttpStatus.OK));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = postService.updatePost("Bearer token", 22L, new UpdatePostRequest(" Changed ", null, PostType.TEXT, null));
        var hidden = postService.changeVisibility("Bearer token", 22L, PostVisibility.PRIVATE);
        postService.deletePost("Bearer token", 22L);

        assertEquals("Changed", updated.content());
        assertEquals(PostVisibility.PRIVATE, hidden.visibility());
        assertTrueDeleted(post);
        verify(restTemplate).exchange(eq("http://search/search/index/22"), eq(HttpMethod.DELETE), eq(HttpEntity.EMPTY), eq(Void.class));
    }

    @Test
    void countersDoNotGoBelowZero() {
        Post post = post(33L, 4L, PostVisibility.PUBLIC);
        post.setLikesCount(0);
        post.setCommentsCount(0);
        when(postRepository.findByPostIdAndIsDeletedFalse(33L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1, postService.incrementLikes(33L).likesCount());
        assertEquals(0, postService.decrementLikes(33L).likesCount());
        assertEquals(1, postService.incrementComments(33L).commentsCount());
        assertEquals(0, postService.decrementComments(33L).commentsCount());
    }

    @Test
    void adminSearchFiltersAndSortsPosts() {
        stubAdmin();
        Post older = post(1L, 2L, PostVisibility.PUBLIC);
        older.setContent("alpha");
        older.setCreatedAt(Instant.parse("2026-05-04T00:00:00Z"));
        Post newer = post(2L, 3L, PostVisibility.PUBLIC);
        newer.setContent("Alpha match");
        newer.setAuthorUsername("writer");
        newer.setCreatedAt(Instant.parse("2026-05-05T00:00:00Z"));
        when(postRepository.findAll()).thenReturn(List.of(older, newer));

        var results = postService.searchPostsForAdmin("Bearer token", "alpha");

        assertEquals(List.of(2L, 1L), results.stream().map(response -> response.postId()).toList());
    }

    @Test
    void publicSearchAndUserPostsRespectVisibility() {
        Post publicPost = post(1L, 2L, PostVisibility.PUBLIC);
        Post privatePost = post(2L, 2L, PostVisibility.PRIVATE);
        when(postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(2L)).thenReturn(List.of(publicPost, privatePost));
        when(postRepository.searchByContent("hello")).thenReturn(List.of(publicPost, privatePost));

        assertEquals(List.of(1L), postService.getPostsByUser(null, 2L).stream().map(response -> response.postId()).toList());
        assertEquals(List.of(1L), postService.searchPosts(null, " hello ").stream().map(response -> response.postId()).toList());
    }

    @Test
    void missingAuthorSnapshotDeletesPostAndCleansSearchIndex() {
        Post post = post(55L, 12L, PostVisibility.PUBLIC);
        when(postRepository.findByPostIdAndIsDeletedFalse(55L)).thenReturn(Optional.of(post));
        doThrow(org.springframework.web.client.HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                null
        ))
                .when(restTemplate)
                .getForObject("http://auth/auth/users/12", Map.class);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> postService.getPostById(null, 55L)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(postRepository).delete(post);
        verify(restTemplate).exchange(eq("http://search/search/index/55"), eq(HttpMethod.DELETE), eq(HttpEntity.EMPTY), eq(Void.class));
    }

    @Test
    void deletePostsByAuthorAllowsOwnerAndRejectsOtherUsers() {
        Post owned = post(60L, 8L, PostVisibility.PUBLIC);
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile(8L, "owner", "USER"), HttpStatus.OK));
        when(postRepository.findByAuthorId(8L)).thenReturn(List.of(owned));

        postService.deletePostsByAuthor("Bearer owner-token", 8L);

        verify(postRepository).deleteAll(List.of(owned));

        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile(9L, "other", "USER"), HttpStatus.OK));

        ResponseStatusException forbidden = assertThrows(
                ResponseStatusException.class,
                () -> postService.deletePostsByAuthor("Bearer other-token", 8L)
        );
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    }

    @Test
    void guestCannotCreateButCanReadPublicFeedAnonymously() {
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile(5L, "guest", "GUEST"), HttpStatus.OK));

        ResponseStatusException createError = assertThrows(
                ResponseStatusException.class,
                () -> postService.createPost("Bearer guest-token", new CreatePostRequest("Read only", List.of(), PostType.TEXT, PostVisibility.PUBLIC))
        );
        assertEquals(HttpStatus.FORBIDDEN, createError.getStatusCode());

        Post publicPost = post(70L, 4L, PostVisibility.PUBLIC);
        when(postRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(PostVisibility.PUBLIC))
                .thenReturn(List.of(publicPost));

        assertEquals(List.of(70L), postService.getFeedForUser("Bearer guest-token").stream().map(response -> response.postId()).toList());
    }

    @Test
    void adminCanDeletePostsByAuthorAndNoOpsWhenNoneExist() {
        stubAdmin();
        Post first = post(1L, 8L, PostVisibility.PUBLIC);
        Post second = post(2L, 8L, PostVisibility.PUBLIC);
        when(postRepository.findByAuthorId(8L)).thenReturn(List.of(first, second));

        postService.deletePostsByAuthor("Bearer token", 8L);

        verify(postRepository).deleteAll(List.of(first, second));
        verify(restTemplate).exchange(eq("http://search/search/index/1"), eq(HttpMethod.DELETE), eq(HttpEntity.EMPTY), eq(Void.class));
        verify(restTemplate).exchange(eq("http://search/search/index/2"), eq(HttpMethod.DELETE), eq(HttpEntity.EMPTY), eq(Void.class));

        when(postRepository.findByAuthorId(9L)).thenReturn(List.of());
        postService.deletePostsByAuthor("Bearer token", 9L);
    }

    @Test
    void adminDeleteBlankSearchAndCountUseExpectedRepositoryPaths() {
        stubAdmin();
        Post post = post(44L, 6L, PostVisibility.PUBLIC);
        when(postRepository.findByPostIdAndIsDeletedFalse(44L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(PostVisibility.PUBLIC)).thenReturn(List.of(post));
        when(postRepository.countByIsDeletedFalse()).thenReturn(12L);

        postService.deletePostAsAdmin("Bearer token", 44L);
        assertTrueDeleted(post);
        assertEquals(List.of(44L), postService.searchPosts(null, " ").stream().map(response -> response.postId()).toList());
        assertEquals(12L, postService.getPostCount());
    }

    @Test
    void backfillAuthorSnapshotsUsesAuthDataAndFallback() {
        stubAdmin();
        Post found = post(1L, 10L, PostVisibility.PUBLIC);
        found.setAuthorUsername(null);
        Post missing = post(2L, 11L, PostVisibility.PUBLIC);
        missing.setAuthorUsername(null);
        when(postRepository.findWithMissingAuthorSnapshot()).thenReturn(List.of(found, missing));
        when(restTemplate.getForObject("http://auth/auth/users/10", Map.class))
                .thenReturn(Map.of("username", " updated ", "fullName", " User Ten ", "profilePicUrl", " pic "));
        doThrow(new RuntimeException("down")).when(restTemplate).getForObject("http://auth/auth/users/11", Map.class);

        postService.backfillAuthorSnapshots("Bearer token");

        assertEquals("updated", found.getAuthorUsername());
        assertEquals("User 11", missing.getAuthorUsername());
        verify(postRepository).saveAll(List.of(found, missing));
    }

    private void stubAdmin() {
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile(1L, "admin", "ADMIN"), HttpStatus.OK));
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

    private Post post(Long postId, Long authorId, PostVisibility visibility) {
        Post post = new Post();
        post.setPostId(postId);
        post.setAuthorId(authorId);
        post.setAuthorUsername("writer" + authorId);
        post.setContent("Old content");
        post.setMediaUrls(Arrays.asList("https://cdn.example.com/old.jpg"));
        post.setPostType(PostType.MEDIA);
        post.setVisibility(visibility);
        post.setLikesCount(2);
        post.setCommentsCount(1);
        post.setSharesCount(0);
        post.setDeleted(false);
        return post;
    }

    private void assertTrueDeleted(Post post) {
        org.junit.jupiter.api.Assertions.assertTrue(post.isDeleted());
    }
}
