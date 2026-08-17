package com.skateboard.podcast.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringPostPlatformLinkRepository extends JpaRepository<PostPlatformLinkJpaEntity, UUID> {

    List<PostPlatformLinkJpaEntity> findByPostId(UUID postId);

    List<PostPlatformLinkJpaEntity> findByPostIdIn(Collection<UUID> postIds);

    Optional<PostPlatformLinkJpaEntity> findByPlatformAndExternalId(String platform, String externalId);

    @Modifying
    @Transactional
    @Query("DELETE FROM PostPlatformLinkJpaEntity l WHERE l.postId = :postId")
    void deleteByPostId(@Param("postId") UUID postId);
}
