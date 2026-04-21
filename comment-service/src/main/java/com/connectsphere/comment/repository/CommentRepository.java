package com.connectsphere.comment.repository;

import com.connectsphere.comment.entity.Comment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

  Optional<Comment> findByCommentId(Long commentId);

    Optional<Comment> findByCommentIdAndDeletedFalse(Long commentId);

    List<Comment> findByPostIdAndDeletedFalseOrderByCreatedAtAsc(Long postId);

    @Query("""
            SELECT c FROM Comment c
            WHERE c.postId = :postId
              AND c.parentCommentId IS NULL
              AND c.deleted = false
            ORDER BY c.createdAt ASC
            """)
    List<Comment> findTopLevelByPostId(@Param("postId") Long postId);

    List<Comment> findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(Long parentCommentId);

    List<Comment> findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(Long authorId);

    long countByPostIdAndDeletedFalse(Long postId);
}
