package com.skateboard.podcast.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.application.port.out.SaveGuestApplicationPort;
import com.skateboard.podcast.domain.exception.DuplicateActiveApplicationException;
import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.domain.model.GuestApplicationStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class GuestApplicationPersistenceAdapter implements LoadGuestApplicationPort, SaveGuestApplicationPort {

    private final SpringGuestApplicationRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public GuestApplicationPersistenceAdapter(SpringGuestApplicationRepository jpaRepository,
                                              ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public GuestApplication save(GuestApplication application) {
        try {
            GuestApplicationJpaEntity saved = jpaRepository.save(toEntity(application));
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // ux_guest_applications_active_user — the atomicity backstop behind
            // SubmitGuestApplicationService's pre-check (see its javadoc).
            throw new DuplicateActiveApplicationException(application.getUserId());
        }
    }

    @Override
    public Optional<GuestApplication> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<GuestApplication> findActiveByUserId(UUID userId) {
        return jpaRepository.findActiveByUserId(userId).map(this::toDomain);
    }

    @Override
    public Optional<GuestApplication> findLatestByUserId(UUID userId) {
        return jpaRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).map(this::toDomain);
    }

    @Override
    public List<GuestApplication> findAll(GuestApplicationStatus status, int page, int size) {
        return jpaRepository.search(status != null ? status.name() : null, PageRequest.of(page, size))
                .getContent().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countAll(GuestApplicationStatus status) {
        return jpaRepository.countAll(status != null ? status.name() : null);
    }

    @Override
    public List<GuestApplication> findAwaitingNotification(int limit) {
        return jpaRepository.findAwaitingNotification(PageRequest.of(0, limit))
                .getContent().stream()
                .map(this::toDomain)
                .toList();
    }

    private GuestApplication toDomain(GuestApplicationJpaEntity e) {
        return GuestApplication.reconstitute(
                e.getId(), e.getUserId(), e.getName(), e.getEmail(), e.getMessage(),
                parseSocialLinks(e.getSocialLinksJson()),
                GuestApplicationStatus.valueOf(e.getStatus()),
                e.getCreatedAt(), e.getUpdatedAt(), e.getReviewedBy(), e.getNotifiedAt());
    }

    private GuestApplicationJpaEntity toEntity(GuestApplication a) {
        GuestApplicationJpaEntity e = new GuestApplicationJpaEntity();
        e.setId(a.getId());
        e.setUserId(a.getUserId());
        e.setName(a.getName());
        e.setEmail(a.getEmail());
        e.setMessage(a.getMessage());
        e.setSocialLinksJson(toJson(a.getSocialLinks()));
        e.setStatus(a.getStatus().name());
        e.setCreatedAt(a.getCreatedAt());
        e.setUpdatedAt(a.getUpdatedAt());
        e.setReviewedBy(a.getReviewedBy());
        e.setNotifiedAt(a.getNotifiedAt());
        return e;
    }

    private String toJson(List<String> socialLinks) {
        if (socialLinks == null || socialLinks.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(socialLinks);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseSocialLinks(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
