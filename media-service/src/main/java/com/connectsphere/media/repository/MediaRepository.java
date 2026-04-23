package com.connectsphere.media.repository;

import com.connectsphere.media.entity.Media;
import com.connectsphere.media.entity.MediaType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, Long> {

    Optional<Media> findByMediaIdAndDeletedFalse(Long mediaId);

    List<Media> findByUploadedByAndDeletedFalseOrderByUploadedAtDesc(Long uploadedBy);

    List<Media> findByLinkedPostIdAndDeletedFalseOrderByUploadedAtDesc(Long linkedPostId);

    List<Media> findByMediaTypeAndDeletedFalseOrderByUploadedAtDesc(MediaType mediaType);
}
