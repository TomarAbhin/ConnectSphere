package com.connectsphere.media.repository;

import com.connectsphere.media.entity.Story;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryRepository extends JpaRepository<Story, Long> {

    Optional<Story> findByStoryIdAndActiveTrue(Long storyId);

    List<Story> findByAuthorIdAndActiveTrueOrderByCreatedAtDesc(Long authorId);

    List<Story> findByActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(Instant now);

    List<Story> findByActiveTrueAndExpiresAtBefore(Instant now);
}
