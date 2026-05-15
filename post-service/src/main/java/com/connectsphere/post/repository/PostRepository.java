package com.connectsphere.post.repository;

import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.PostVisibility;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByPostIdAndIsDeletedFalse(Long postId);

    List<Post> findByAuthorId(Long authorId);

    List<Post> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(Long authorId);

    List<Post> findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(PostVisibility visibility);

    List<Post> findByAuthorIdInAndVisibilityInAndIsDeletedFalseOrderByCreatedAtDesc(
            Collection<Long> authorIds,
            Collection<PostVisibility> visibilities
    );

    List<Post> findByAuthorIdAndVisibilityInAndIsDeletedFalseOrderByCreatedAtDesc(
            Long authorId,
            Collection<PostVisibility> visibilities
    );

    @Query("""
            SELECT p FROM Post p
            WHERE p.isDeleted = false
              AND LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY p.createdAt DESC
            """)
    List<Post> searchByContent(@Param("query") String query);

    @Query("""
            SELECT p FROM Post p
            WHERE p.authorUsername IS NULL OR p.authorUsername = ''
            """)
    List<Post> findWithMissingAuthorSnapshot();

    long countByIsDeletedFalse();
}
