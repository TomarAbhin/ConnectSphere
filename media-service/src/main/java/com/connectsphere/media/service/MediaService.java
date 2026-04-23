package com.connectsphere.media.service;

import com.connectsphere.media.dto.CreateStoryRequest;
import com.connectsphere.media.dto.MediaUploadResponse;
import com.connectsphere.media.dto.StoryResponse;
import com.connectsphere.media.dto.UploadMediaRequest;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService {

    MediaUploadResponse uploadMedia(String authorizationHeader, MultipartFile file, UploadMediaRequest request);

    MediaUploadResponse getMediaById(Long mediaId);

    List<MediaUploadResponse> getMediaByPost(Long postId);

    List<MediaUploadResponse> getMediaByUser(String authorizationHeader, Long userId);

    void deleteMedia(String authorizationHeader, Long mediaId);

    StoryResponse createStory(String authorizationHeader, CreateStoryRequest request);

    StoryResponse getStoryById(Long storyId);

    List<StoryResponse> getStoriesByUser(Long userId);

    List<StoryResponse> getActiveStories();

    StoryResponse viewStory(Long storyId);

    void deleteStory(String authorizationHeader, Long storyId);

    int expireOldStories();

    void publishExpiredStoryEvent(com.connectsphere.media.dto.StoryExpiredEvent event);
}
