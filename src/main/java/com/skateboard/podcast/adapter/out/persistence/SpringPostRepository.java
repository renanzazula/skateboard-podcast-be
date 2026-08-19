package com.skateboard.podcast.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringPostRepository extends JpaRepository<PostJpaEntity, UUID> {
    Optional<PostJpaEntity> findBySlug(String slug);
    Optional<PostJpaEntity> findByYoutubeVideoId(String youtubeVideoId);
    long countByStatus(String status);
    boolean existsBySlug(String slug);

    @Query("SELECT p FROM PostJpaEntity p WHERE p.status = :status " +
           "ORDER BY COALESCE(p.publishAt, p.createdAt) DESC")
    Page<PostJpaEntity> findByStatusOrderByEffectivePublishDate(@Param("status") String status, Pageable pageable);

    @Query("SELECT p FROM PostJpaEntity p WHERE p.status = :status " +
           "AND LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY COALESCE(p.publishAt, p.createdAt) DESC")
    Page<PostJpaEntity> searchByStatusAndTitle(@Param("status") String status, @Param("query") String query, Pageable pageable);
}
