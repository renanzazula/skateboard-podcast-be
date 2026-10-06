package com.skateboard.podcast.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringGuestApplicationRepository extends JpaRepository<GuestApplicationJpaEntity, UUID> {

    // NEW/CONTACTED/ACCEPTED — kept in sync with GuestApplicationStatus.isActive()
    // and the ux_guest_applications_active_user partial index.
    @Query("SELECT a FROM GuestApplicationJpaEntity a WHERE a.userId = :userId AND a.status <> 'DECLINED'")
    Optional<GuestApplicationJpaEntity> findActiveByUserId(@Param("userId") UUID userId);

    Optional<GuestApplicationJpaEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    // Named search(), not findAll(String, Pageable): that overload is
    // ambiguous with QueryByExampleExecutor.findAll(Example<S>, Pageable),
    // which JpaRepository also brings in.
    @Query("SELECT a FROM GuestApplicationJpaEntity a WHERE (:status IS NULL OR a.status = :status) " +
           "ORDER BY a.createdAt DESC, a.id")
    Page<GuestApplicationJpaEntity> search(@Param("status") String status, Pageable pageable);

    @Query("SELECT COUNT(a) FROM GuestApplicationJpaEntity a WHERE (:status IS NULL OR a.status = :status)")
    long countAll(@Param("status") String status);

    @Query("SELECT a FROM GuestApplicationJpaEntity a WHERE a.notifiedAt IS NULL ORDER BY a.createdAt, a.id")
    Page<GuestApplicationJpaEntity> findAwaitingNotification(Pageable pageable);
}
