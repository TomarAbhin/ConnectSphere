package com.connectsphere.post.serviceimpl;

import com.connectsphere.post.dto.AuthProfileResponse;
import com.connectsphere.post.dto.UpdatePostRequest;
import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.PostVisibility;
import com.connectsphere.post.repository.PostRepository;
import java.time.Instant;
import java.util.Arrays;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                "",
                "http://notification"
        );
    }

    @Test
    void updatePostAsAdminAllowsAdminToEditPost() {
        AuthProfileResponse adminProfile = new AuthProfileResponse(
                1L,
                "admin",
                "admin@example.com",
                "Admin User",
                null,
                null,
                "ADMIN",
                "LOCAL",
                true,
                Instant.parse("2026-05-05T00:00:00Z")
        );

        Post post = new Post();
        post.setPostId(10L);
        post.setAuthorId(3L);
        post.setAuthorUsername("writer");
        post.setContent("Old content");
        post.setMediaUrls(Arrays.asList("https://cdn.example.com/old.jpg"));
        post.setPostType(PostType.MEDIA);
        post.setVisibility(PostVisibility.PUBLIC);
        post.setLikesCount(2);
        post.setCommentsCount(1);
        post.setSharesCount(0);
        post.setDeleted(false);

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
}