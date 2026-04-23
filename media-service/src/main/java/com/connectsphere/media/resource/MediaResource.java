package com.connectsphere.media.resource;

import com.connectsphere.media.dto.CreateStoryRequest;
import com.connectsphere.media.dto.MediaListResponse;
import com.connectsphere.media.dto.MediaUploadResponse;
import com.connectsphere.media.dto.StoryListResponse;
import com.connectsphere.media.dto.StoryResponse;
import com.connectsphere.media.dto.UploadMediaRequest;
import com.connectsphere.media.service.MediaService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping
public class MediaResource {

    private final MediaService mediaService;
    private final ObjectMapper objectMapper;

    public MediaResource(MediaService mediaService, ObjectMapper objectMapper) {
        this.mediaService = mediaService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaUploadResponse> uploadMedia(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("request") String requestJson
    ) {
        UploadMediaRequest request = parseUploadMediaRequest(requestJson);
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.uploadMedia(authorization, file, request));
    }

    private UploadMediaRequest parseUploadMediaRequest(String requestJson) {
        try {
            return objectMapper.readValue(requestJson, UploadMediaRequest.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid request JSON in multipart field 'request'", ex);
        }
    }

    @GetMapping("/media/{mediaId}")
    public ResponseEntity<MediaUploadResponse> getMediaById(@PathVariable Long mediaId) {
        return ResponseEntity.ok(mediaService.getMediaById(mediaId));
    }

    @GetMapping("/media/files/{*filePath}")
    public ResponseEntity<Resource> downloadMedia(@PathVariable String filePath) throws IOException {
        Path baseDir = Paths.get("uploads/media").toAbsolutePath().normalize();
        Path resolved = baseDir.resolve(filePath).normalize();
        if (!resolved.startsWith(baseDir) || !Files.exists(resolved)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(resolved.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resource);
    }

    @GetMapping("/media/post/{postId}")
    public ResponseEntity<MediaListResponse> getMediaByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(new MediaListResponse(mediaService.getMediaByPost(postId)));
    }

    @GetMapping("/media/user/{userId}")
    public ResponseEntity<MediaListResponse> getMediaByUser(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(new MediaListResponse(mediaService.getMediaByUser(authorization, userId)));
    }

    @DeleteMapping("/media/{mediaId}")
    public ResponseEntity<Void> deleteMedia(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long mediaId
    ) {
        mediaService.deleteMedia(authorization, mediaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stories")
    public ResponseEntity<StoryResponse> createStory(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateStoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.createStory(authorization, request));
    }

    @GetMapping("/stories/{storyId}")
    public ResponseEntity<StoryResponse> getStoryById(@PathVariable Long storyId) {
        return ResponseEntity.ok(mediaService.getStoryById(storyId));
    }

    @GetMapping("/stories/user/{userId}")
    public ResponseEntity<StoryListResponse> getStoriesByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(new StoryListResponse(mediaService.getStoriesByUser(userId)));
    }

    @GetMapping("/stories/active")
    public ResponseEntity<StoryListResponse> getActiveStories() {
        return ResponseEntity.ok(new StoryListResponse(mediaService.getActiveStories()));
    }

    @PutMapping("/stories/{storyId}/view")
    public ResponseEntity<StoryResponse> viewStory(@PathVariable Long storyId) {
        return ResponseEntity.ok(mediaService.viewStory(storyId));
    }

    @DeleteMapping("/stories/{storyId}")
    public ResponseEntity<Void> deleteStory(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long storyId
    ) {
        mediaService.deleteStory(authorization, storyId);
        return ResponseEntity.noContent().build();
    }
}
