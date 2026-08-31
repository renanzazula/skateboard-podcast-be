package com.skateboard.podcast.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SpringPostCategoryRepository extends JpaRepository<PostCategoryJpaEntity, PostCategoryId> {

    @Modifying
    @Transactional
    @Query("DELETE FROM PostCategoryJpaEntity pc WHERE pc.postId = :postId AND pc.categoryId = :categoryId")
    void deleteAssociation(@Param("postId") UUID postId, @Param("categoryId") UUID categoryId);

    @Query("SELECT po.youtubeVideoId FROM PostJpaEntity po " +
           "JOIN PostCategoryJpaEntity pc ON pc.postId = po.id " +
           "WHERE pc.categoryId = :categoryId AND po.youtubeVideoId IS NOT NULL")
    Set<String> findYoutubeVideoIdsByCategoryId(@Param("categoryId") UUID categoryId);

    // Ordered by publish date only — createdAt is the bulk-import timestamp and
    // says nothing about episode order. NULLS LAST so a post still awaiting a
    // publish date sinks to the bottom rather than jumping to the top; id is the
    // stable pagination tiebreaker.
    @Query("SELECT po FROM PostJpaEntity po " +
           "JOIN PostCategoryJpaEntity pc ON pc.postId = po.id " +
           "JOIN CategoryJpaEntity c ON c.id = pc.categoryId " +
           "WHERE c.slug = :slug AND po.status = :status " +
           "ORDER BY po.publishAt DESC NULLS LAST, po.id")
    Page<PostJpaEntity> findByCategorySlugAndStatus(@Param("slug") String slug, @Param("status") String status, Pageable pageable);

    @Query("SELECT COUNT(po) FROM PostJpaEntity po " +
           "JOIN PostCategoryJpaEntity pc ON pc.postId = po.id " +
           "JOIN CategoryJpaEntity c ON c.id = pc.categoryId " +
           "WHERE c.slug = :slug AND po.status = :status")
    long countByCategorySlugAndStatus(@Param("slug") String slug, @Param("status") String status);

    @Query("SELECT pc.categoryId AS categoryId, COUNT(po) AS postCount FROM PostJpaEntity po " +
           "JOIN PostCategoryJpaEntity pc ON pc.postId = po.id " +
           "WHERE po.status = :status " +
           "GROUP BY pc.categoryId")
    List<CategoryPostCount> countByCategoryIdAndStatus(@Param("status") String status);

    interface CategoryPostCount {
        UUID getCategoryId();
        long getPostCount();
    }
}
