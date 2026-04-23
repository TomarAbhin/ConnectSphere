package com.connectsphere.search.repository;

import com.connectsphere.search.entity.Hashtag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    Optional<Hashtag> findByTagIgnoreCase(String tag);

    List<Hashtag> findByTagContainingIgnoreCaseOrderByTagAsc(String query);

    List<Hashtag> findTop20ByOrderByPostCountDescLastUsedAtDesc();

    List<Hashtag> findByPostCountGreaterThanOrderByPostCountDescLastUsedAtDesc(long minimumCount, Pageable pageable);
}