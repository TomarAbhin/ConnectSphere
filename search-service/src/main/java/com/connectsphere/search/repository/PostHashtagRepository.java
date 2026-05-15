package com.connectsphere.search.repository;

import com.connectsphere.search.entity.PostHashtag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, Long> {

    List<PostHashtag> findByPost_PostId(Long postId);

    void deleteByPost_PostId(Long postId);

    @Query("select count(ph) from PostHashtag ph where lower(ph.hashtag.tag) = lower(:tag) and ph.post.deleted = false")
    long countPostsByHashtag(@Param("tag") String tag);

    @Query("select ph.post.postId from PostHashtag ph where lower(ph.hashtag.tag) = lower(:tag)")
    List<Long> findPostIdsByHashtag(@Param("tag") String tag);

    @Query("select ph from PostHashtag ph where ph.hashtag.tag = :tag order by ph.createdAt desc")
    List<PostHashtag> findByHashtagTagOrderByCreatedAtDesc(@Param("tag") String tag);
}