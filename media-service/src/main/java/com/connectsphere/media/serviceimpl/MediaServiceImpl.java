package com.connectsphere.media.serviceimpl;

import com.connectsphere.media.dto.AuthProfileResponse;
import com.connectsphere.media.dto.CreateStoryRequest;
import com.connectsphere.media.dto.MediaUploadResponse;
import com.connectsphere.media.dto.StoryResponse;
import com.connectsphere.media.dto.StoryExpiredEvent;
import com.connectsphere.media.dto.UploadMediaRequest;
import com.connectsphere.media.dto.UserResponse;
import com.connectsphere.media.entity.Media;
import com.connectsphere.media.entity.MediaType;
import com.connectsphere.media.entity.Story;
import com.connectsphere.media.repository.MediaRepository;
import com.connectsphere.media.repository.StoryRepository;
import com.connectsphere.media.service.MediaService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Service
@Transactional
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;
    private final StoryRepository storyRepository;
    private final RestTemplate restTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final String authServiceUrl;
    private final String rabbitExchange;
    private final String storyExpiredRoutingKey;
    private final Path uploadDir;

    public MediaServiceImpl(
            MediaRepository mediaRepository,
            StoryRepository storyRepository,
            RestTemplate restTemplate,
            RabbitTemplate rabbitTemplate,
            @Value("${app.services.auth-service.url:http://localhost:8081}") String authServiceUrl,
            @Value("${app.media.upload-dir:uploads/media}") String uploadDir,
            @Value("${app.rabbitmq.exchange}") String rabbitExchange,
            @Value("${app.rabbitmq.routing-key.story-expired}") String storyExpiredRoutingKey
    ) {
        this.mediaRepository = mediaRepository;
        this.storyRepository = storyRepository;
        this.restTemplate = restTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.authServiceUrl = authServiceUrl;
        this.rabbitExchange = rabbitExchange;
        this.storyExpiredRoutingKey = storyExpiredRoutingKey;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize media upload directory", ex);
        }
    }

    @Override
    public MediaUploadResponse uploadMedia(String authorizationHeader, MultipartFile file, UploadMediaRequest request) {
        AuthProfileResponse profile = resolveCurrentProfile(authorizationHeader);
        validateFile(file);

        String storedFileName = buildStoredFileName(profile.userId(), file.getOriginalFilename());
        Path destination = uploadDir.resolve(storedFileName).normalize();
        storeLocally(destination, file);
        String publicUrl = resolvePublicUrl(storedFileName);

        Media media = new Media();
        media.setUploadedBy(profile.userId());
        media.setUrl(publicUrl);
        media.setSizeKb(Math.max(1L, Math.round(file.getSize() / 1024.0)));
        media.setMediaType(request.mediaType());
        media.setLinkedPostId(request.linkedPostId());
        Media saved = mediaRepository.save(media);
        return toMediaResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MediaUploadResponse getMediaById(Long mediaId) {
        return toMediaResponse(getActiveMedia(mediaId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaUploadResponse> getMediaByPost(Long postId) {
        return mediaRepository.findByLinkedPostIdAndDeletedFalseOrderByUploadedAtDesc(postId)
                .stream()
                .map(this::toMediaResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaUploadResponse> getMediaByUser(String authorizationHeader, Long userId) {
        ensureUserExists(userId, authorizationHeader);
        return mediaRepository.findByUploadedByAndDeletedFalseOrderByUploadedAtDesc(userId)
                .stream()
                .map(this::toMediaResponse)
                .toList();
    }

    @Override
    public void deleteMedia(String authorizationHeader, Long mediaId) {
        Media media = getOwnedMedia(authorizationHeader, mediaId);
        media.setDeleted(true);
        mediaRepository.save(media);
        try {
            Path filePath = resolveLocalFilePath(media.getUrl());
            Files.deleteIfExists(filePath);
        } catch (Exception ignored) {
        }
    }

    @Override
    public StoryResponse createStory(String authorizationHeader, CreateStoryRequest request) {
        AuthProfileResponse profile = resolveCurrentProfile(authorizationHeader);
        Story story = new Story();
        story.setAuthorId(profile.userId());
        story.setMediaUrl(request.mediaUrl().trim());
        story.setCaption(request.caption() == null ? null : request.caption().trim());
        story.setMediaType(request.mediaType());
        story.setViewsCount(0L);
        story.setActive(true);
        story.setExpiresAt(Instant.now().plusSeconds(24 * 60 * 60));
        return toStoryResponse(storyRepository.save(story));
    }

    @Override
    @Transactional(readOnly = true)
    public StoryResponse getStoryById(Long storyId) {
        return toStoryResponse(getActiveStory(storyId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryResponse> getStoriesByUser(Long userId) {
        return storyRepository.findByAuthorIdAndActiveTrueOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toStoryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryResponse> getActiveStories() {
        Instant now = Instant.now();
        return storyRepository.findByActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(now)
                .stream()
                .map(this::toStoryResponse)
                .toList();
    }

    @Override
    public StoryResponse viewStory(Long storyId) {
        Story story = getActiveStory(storyId);
        story.setViewsCount(story.getViewsCount() + 1);
        return toStoryResponse(storyRepository.save(story));
    }

    @Override
    public void deleteStory(String authorizationHeader, Long storyId) {
        Story story = getOwnedStory(authorizationHeader, storyId);
        story.setActive(false);
        storyRepository.save(story);
    }

    @Override
    public int expireOldStories() {
        Instant now = Instant.now();
        List<Story> expiredStories = storyRepository.findByActiveTrueAndExpiresAtBefore(now);
        if (expiredStories.isEmpty()) {
            return 0;
        }
        expiredStories.forEach(story -> {
            story.setActive(false);
            publishExpiredStoryEvent(new StoryExpiredEvent(story.getStoryId(), story.getAuthorId(), story.getMediaUrl(), now));
        });
        storyRepository.saveAll(expiredStories);
        return expiredStories.size();
    }

    @Override
    public void publishExpiredStoryEvent(StoryExpiredEvent event) {
        try {
            rabbitTemplate.convertAndSend(rabbitExchange, storyExpiredRoutingKey, event);
        } catch (Exception ignored) {
        }
    }

    private Media getActiveMedia(Long mediaId) {
        return mediaRepository.findByMediaIdAndDeletedFalse(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
    }

    private Media getOwnedMedia(String authorizationHeader, Long mediaId) {
        Media media = getActiveMedia(mediaId);
        Long currentUserId = resolveCurrentProfile(authorizationHeader).userId();
        if (!Objects.equals(media.getUploadedBy(), currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only modify your own media");
        }
        return media;
    }

    private Story getActiveStory(Long storyId) {
        Story story = storyRepository.findByStoryIdAndActiveTrue(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));
        if (story.getExpiresAt() != null && story.getExpiresAt().isBefore(Instant.now())) {
            story.setActive(false);
            storyRepository.save(story);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Story has expired");
        }
        return story;
    }

    private Story getOwnedStory(String authorizationHeader, Long storyId) {
        Story story = getActiveStory(storyId);
        Long currentUserId = resolveCurrentProfile(authorizationHeader).userId();
        if (!Objects.equals(story.getAuthorId(), currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only modify your own story");
        }
        return story;
    }

    private void ensureUserExists(Long userId, String authorizationHeader) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
        }
        fetchUserById(authorizationHeader, userId);
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
            if (profile == null || profile.userId() == null || !profile.active()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
            }
            return profile;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
    }

    private UserResponse fetchUserById(String authorizationHeader, Long userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            UserResponse user = restTemplate.exchange(
                    authServiceUrl + "/auth/users/" + userId,
                    HttpMethod.GET,
                    request,
                    UserResponse.class
            ).getBody();
            if (user == null || !user.active()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            return user;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
    }

    private void storeLocally(Path destination, MultipartFile file) {
        try {
            Files.createDirectories(destination.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to upload media");
        }
    }

    private String buildStoredFileName(Long userId, String originalFilename) {
        String safeName = originalFilename == null ? "file" : originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "media/" + userId + "/" + UUID.randomUUID() + "-" + safeName;
    }

    private String resolvePublicUrl(String storedFileName) {
        return "/media/files/" + storedFileName.replace("\\", "/");
    }

    private Path resolveLocalFilePath(String url) {
        if (url == null || url.isBlank()) {
            return uploadDir.resolve("missing");
        }
        if (url.startsWith("/media/files/")) {
            String relativePath = url.substring("/media/files/".length());
            return uploadDir.resolve(relativePath).normalize();
        }
        URL parsed = null;
        try {
            parsed = new URL(url);
        } catch (Exception ignored) {
        }
        if (parsed == null) {
            return uploadDir.resolve(url).normalize();
        }
        String path = parsed.getPath();
        String relative = path != null && path.startsWith("/") ? path.substring(1) : path;
        return uploadDir.resolve(relative).normalize();
    }

    private MediaUploadResponse toMediaResponse(Media media) {
        return new MediaUploadResponse(
                media.getMediaId(),
                media.getUploadedBy(),
                media.getUrl(),
                media.getSizeKb(),
                media.getMediaType(),
                media.getLinkedPostId(),
                media.getUploadedAt(),
                media.isDeleted()
        );
    }

    private StoryResponse toStoryResponse(Story story) {
        return new StoryResponse(
                story.getStoryId(),
                story.getAuthorId(),
                story.getMediaUrl(),
                story.getCaption(),
                story.getMediaType(),
                story.getViewsCount(),
                story.getExpiresAt(),
                story.getCreatedAt(),
                story.isActive()
        );
    }
}
