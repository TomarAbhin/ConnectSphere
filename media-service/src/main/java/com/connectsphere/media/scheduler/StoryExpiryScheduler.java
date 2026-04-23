package com.connectsphere.media.scheduler;

import com.connectsphere.media.service.MediaService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StoryExpiryScheduler {

    private final MediaService mediaService;

    public StoryExpiryScheduler(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Scheduled(cron = "${app.scheduler.story-expiry-cron}")
    public void expireStories() {
        mediaService.expireOldStories();
    }
}
