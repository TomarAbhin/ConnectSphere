package com.connectsphere.media.serviceimpl;

import com.connectsphere.media.dto.AuthProfileResponse;
import com.connectsphere.media.dto.CreateStoryRequest;
import com.connectsphere.media.dto.UploadMediaRequest;
import com.connectsphere.media.dto.UserResponse;
import com.connectsphere.media.entity.Media;
import com.connectsphere.media.entity.MediaType;
import com.connectsphere.media.entity.Story;
import com.connectsphere.media.repository.MediaRepository;
import com.connectsphere.media.repository.StoryRepository;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private RestTemplate restTemplate;

    @TempDir
    Path tempDir;

    @Test
    void expireOldStoriesMarksExpiredStoriesInactive() throws Exception {
        ObjectProvider<RabbitTemplate> rabbitTemplateProvider = mock(ObjectProvider.class);
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(null);
        MediaServiceImpl service = new MediaServiceImpl(
                mediaRepository,
                storyRepository,
                restTemplate,
                rabbitTemplateProvider,
                "http://localhost:8081",
                tempDir.resolve("uploads/media").toString(),
                "connectsphere.exchange",
                "story.expired"
        );

        Story expired = new Story();
        expired.setStoryId(10L);
        expired.setAuthorId(7L);
        expired.setMediaUrl("/media/files/example.jpg");
        expired.setActive(true);
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        expired.setViewsCount(3L);

        when(storyRepository.findByActiveTrueAndExpiresAtBefore(any(Instant.class))).thenReturn(List.of(expired));
        when(storyRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int expiredCount = service.expireOldStories();

        assertEquals(1, expiredCount);
        assertFalse(expired.isActive());
        verify(storyRepository).saveAll(List.of(expired));
        verify(mediaRepository, never()).save(any());
        assertTrue(Files.exists(tempDir.resolve("uploads/media")));
    }

    @Test
    void uploadMediaStoresFileAndReturnsPublicUrl() throws Exception {
        MediaServiceImpl service = service(null);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("bad name.png");
        when(file.getSize()).thenReturn(2048L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("image".getBytes()));
        stubProfile();
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0);
            media.setMediaId(101L);
            return media;
        });

        var response = service.uploadMedia("Bearer token", file, new UploadMediaRequest(22L, MediaType.IMAGE));

        assertEquals(101L, response.mediaId());
        assertEquals(2L, response.sizeKb());
        assertTrue(response.url().startsWith("/media/files/media/7/"));
        assertTrue(Files.exists(tempDir.resolve("uploads/media")));
    }

    @Test
    void mediaQueriesAndDeleteUseOwnership() {
        MediaServiceImpl service = service(null);
        Media media = media(1L, 7L, "/media/files/media/7/file.png");
        stubProfile();
        stubUser(7L);
        when(mediaRepository.findByMediaIdAndDeletedFalse(1L)).thenReturn(Optional.of(media));
        when(mediaRepository.findByLinkedPostIdAndDeletedFalseOrderByUploadedAtDesc(22L)).thenReturn(List.of(media));
        when(mediaRepository.findByUploadedByAndDeletedFalseOrderByUploadedAtDesc(7L)).thenReturn(List.of(media));
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1L, service.getMediaById(1L).mediaId());
        assertEquals(1, service.getMediaByPost(22L).size());
        assertEquals(1, service.getMediaByUser("Bearer token", 7L).size());
        service.deleteMedia("Bearer token", 1L);
        assertTrue(media.isDeleted());
    }

    @Test
    void storyLifecycleCoversCreateViewLikesDeleteAndExpiry() {
        MediaServiceImpl service = service(null);
        stubProfile();
        Story story = story(8L, 7L);
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> {
            Story saved = invocation.getArgument(0);
            saved.setStoryId(8L);
            return saved;
        });
        when(storyRepository.findByStoryIdAndActiveTrue(8L)).thenReturn(Optional.of(story));
        when(storyRepository.findByAuthorIdAndActiveTrueOrderByCreatedAtDesc(7L)).thenReturn(List.of(story));
        when(storyRepository.findByActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(any(Instant.class))).thenReturn(List.of(story));

        assertEquals(8L, service.createStory("Bearer token", new CreateStoryRequest(" /m.png ", " hi ", MediaType.IMAGE)).storyId());
        assertEquals(8L, service.getStoryById(8L).storyId());
        assertEquals(1, service.getStoriesByUser(7L).size());
        assertEquals(1, service.getActiveStories().size());
        assertEquals(1L, service.viewStory(8L).viewsCount());
        assertEquals(1L, service.incrementStoryLikes(8L).likesCount());
        assertEquals(0L, service.decrementStoryLikes(8L).likesCount());
        service.deleteStory("Bearer token", 8L);
        assertFalse(story.isActive());
    }

    @Test
    void expiredStoryThrowsNotFoundAndPublishesWhenRabbitAvailable() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MediaServiceImpl service = service(rabbitTemplate);
        Story expired = story(9L, 7L);
        expired.setExpiresAt(Instant.now().minusSeconds(5));
        when(storyRepository.findByStoryIdAndActiveTrue(9L)).thenReturn(Optional.of(expired));
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(ResponseStatusException.class, () -> service.getStoryById(9L));
        assertFalse(expired.isActive());

        service.publishExpiredStoryEvent(new com.connectsphere.media.dto.StoryExpiredEvent(9L, 7L, "/m.png", Instant.now()));
        verify(rabbitTemplate).convertAndSend(eq("connectsphere.exchange"), eq("story.expired"), any(Object.class));
    }

    private MediaServiceImpl service(RabbitTemplate rabbitTemplate) {
        ObjectProvider<RabbitTemplate> rabbitTemplateProvider = mock(ObjectProvider.class);
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(rabbitTemplate);
        return new MediaServiceImpl(
                mediaRepository,
                storyRepository,
                restTemplate,
                rabbitTemplateProvider,
                "http://localhost:8081",
                tempDir.resolve("uploads/media").toString(),
                "connectsphere.exchange",
                "story.expired"
        );
    }

    private void stubProfile() {
        AuthProfileResponse profile = new AuthProfileResponse(
                7L,
                "author",
                "author@example.com",
                "Author Name",
                null,
                null,
                "USER",
                "LOCAL",
                true,
                Instant.parse("2026-05-05T00:00:00Z")
        );
        when(restTemplate.exchange(
                eq("http://localhost:8081/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));
    }

    private void stubUser(Long userId) {
        UserResponse user = new UserResponse(
                userId,
                "author",
                "author@example.com",
                "Author Name",
                null,
                null,
                "USER",
                "LOCAL",
                true
        );
        when(restTemplate.exchange(
                eq("http://localhost:8081/auth/users/" + userId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserResponse.class)
        )).thenReturn(new ResponseEntity<>(user, HttpStatus.OK));
    }

    private Media media(Long mediaId, Long uploadedBy, String url) {
        Media media = new Media();
        media.setMediaId(mediaId);
        media.setUploadedBy(uploadedBy);
        media.setUrl(url);
        media.setSizeKb(1L);
        media.setMediaType(MediaType.IMAGE);
        media.setLinkedPostId(22L);
        media.setDeleted(false);
        return media;
    }

    private Story story(Long storyId, Long authorId) {
        Story story = new Story();
        story.setStoryId(storyId);
        story.setAuthorId(authorId);
        story.setAuthorUsername("author");
        story.setAuthorFullName("Author Name");
        story.setMediaUrl("/media/files/story.png");
        story.setCaption("caption");
        story.setMediaType(MediaType.IMAGE);
        story.setViewsCount(0L);
        story.setLikesCount(0L);
        story.setActive(true);
        story.setExpiresAt(Instant.now().plusSeconds(3600));
        return story;
    }
}
