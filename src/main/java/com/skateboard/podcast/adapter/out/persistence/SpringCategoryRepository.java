package com.skateboard.podcast.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringCategoryRepository extends JpaRepository<CategoryJpaEntity, UUID> {
    Optional<CategoryJpaEntity> findBySourceAndExternalId(String source, String externalId);
    Optional<CategoryJpaEntity> findBySlug(String slug);
    boolean existsBySlug(String slug);

    @Query("SELECT c FROM CategoryJpaEntity c WHERE c.enabled = true " +
           "ORDER BY c.isDefault DESC, c.createdAt ASC")
    List<CategoryJpaEntity> findAllEnabledOrdered();
}
