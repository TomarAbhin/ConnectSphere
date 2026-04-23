package com.connectsphere.search.repository;

import com.connectsphere.search.entity.IndexedPost;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchRepository extends JpaRepository<IndexedPost, Long> {

    Optional<IndexedPost> findByPostId(Long postId);

    List<IndexedPost> findByDeletedFalseOrderByCreatedAtDesc();

    List<IndexedPost> findByDeletedFalseAndContentContainingIgnoreCaseOrderByCreatedAtDesc(String query);

    List<IndexedPost> findByDeletedFalseAndPostIdInOrderByCreatedAtDesc(Collection<Long> postIds);
}