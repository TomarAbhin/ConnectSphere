package com.connectsphere.media.serviceimpl;

import com.connectsphere.media.entity.Story;
import com.connectsphere.media.repository.MediaRepository;
import com.connectsphere.media.repository.StoryRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}