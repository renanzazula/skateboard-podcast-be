package com.skateboard.podcast.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skateboard.podcast.domain.exception.DuplicateActiveApplicationException;
import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.domain.model.GuestApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestApplicationPersistenceAdapterTest {

    @Mock private SpringGuestApplicationRepository jpaRepository;

    private GuestApplicationPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new GuestApplicationPersistenceAdapter(jpaRepository, new ObjectMapper());
    }

    private GuestApplicationJpaEntity entity(UUID id, UUID userId, String status) {
        GuestApplicationJpaEntity e = new GuestApplicationJpaEntity();
        e.setId(id);
        e.setUserId(userId);
        e.setName("Jane Doe");
        e.setEmail("jane@example.com");
        e.setMessage("I love skating");
        e.setSocialLinksJson("[\"https://instagram.com/jane\"]");
        e.setStatus(status);
        e.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        e.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return e;
    }

    @Test
    void savePersistsAndMapsBackToDomain() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(jpaRepository.save(any())).thenReturn(entity(id, userId, "NEW"));

        GuestApplication application = GuestApplication.submit(userId, "Jane Doe", "jane@example.com",
                "I love skating", List.of("https://instagram.com/jane"));

        GuestApplication saved = adapter.save(application);

        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getSocialLinks()).containsExactly("https://instagram.com/jane");
        assertThat(saved.getStatus()).isEqualTo(GuestApplicationStatus.NEW);

        ArgumentCaptor<GuestApplicationJpaEntity> captor = ArgumentCaptor.forClass(GuestApplicationJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getSocialLinksJson()).isEqualTo("[\"https://instagram.com/jane\"]");
    }

    @Test
    void saveDefaultsEmptySocialLinksToAnEmptyJsonArray() {
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GuestApplication application = GuestApplication.submit(UUID.randomUUID(), "Jane Doe", "jane@example.com",
                "I love skating", null);
        adapter.save(application);

        ArgumentCaptor<GuestApplicationJpaEntity> captor = ArgumentCaptor.forClass(GuestApplicationJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getSocialLinksJson()).isEqualTo("[]");
    }

    @Test
    void saveTranslatesAConstraintViolationIntoADuplicateActiveApplicationException() {
        UUID userId = UUID.randomUUID();
        when(jpaRepository.save(any())).thenThrow(new DataIntegrityViolationException("ux_guest_applications_active_user"));

        GuestApplication application = GuestApplication.submit(userId, "Jane Doe", "jane@example.com", "msg", null);

        assertThatThrownBy(() -> adapter.save(application))
                .isInstanceOf(DuplicateActiveApplicationException.class)
                .hasMessageContaining(userId.toString());
    }

    @Test
    void findByIdMapsAnExistingRow() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity(id, UUID.randomUUID(), "CONTACTED")));

        Optional<GuestApplication> result = adapter.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(GuestApplicationStatus.CONTACTED);
    }

    @Test
    void findByIdIsEmptyWhenAbsent() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void findActiveByUserIdDelegatesToTheRepository() {
        UUID userId = UUID.randomUUID();
        when(jpaRepository.findActiveByUserId(userId)).thenReturn(Optional.of(entity(UUID.randomUUID(), userId, "NEW")));

        assertThat(adapter.findActiveByUserId(userId)).isPresent();
    }

    @Test
    void findLatestByUserIdDelegatesToTheRepository() {
        UUID userId = UUID.randomUUID();
        when(jpaRepository.findFirstByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(entity(UUID.randomUUID(), userId, "DECLINED")));

        assertThat(adapter.findLatestByUserId(userId)).isPresent();
    }

    @Test
    void findAllPassesTheStatusNameAndPagesThrough() {
        Page<GuestApplicationJpaEntity> page = new PageImpl<>(
                List.of(entity(UUID.randomUUID(), UUID.randomUUID(), "NEW")));
        when(jpaRepository.search(eq("NEW"), eq(PageRequest.of(0, 10)))).thenReturn(page);

        List<GuestApplication> result = adapter.findAll(GuestApplicationStatus.NEW, 0, 10);

        assertThat(result).hasSize(1);
    }

    @Test
    void findAllWithNoStatusPassesNullThrough() {
        when(jpaRepository.search(isNull(), eq(PageRequest.of(0, 10)))).thenReturn(new PageImpl<>(List.of()));

        assertThat(adapter.findAll(null, 0, 10)).isEmpty();
        verify(jpaRepository).search(isNull(), eq(PageRequest.of(0, 10)));
    }

    @Test
    void countAllPassesTheStatusNameThrough() {
        when(jpaRepository.countAll("ACCEPTED")).thenReturn(3L);

        assertThat(adapter.countAll(GuestApplicationStatus.ACCEPTED)).isEqualTo(3L);
    }

    @Test
    void findAwaitingNotificationMapsThePage() {
        when(jpaRepository.findAwaitingNotification(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(entity(UUID.randomUUID(), UUID.randomUUID(), "NEW"))));

        assertThat(adapter.findAwaitingNotification(20)).hasSize(1);
    }

    @Test
    void parsesAMalformedSocialLinksJsonAsEmpty() {
        UUID id = UUID.randomUUID();
        GuestApplicationJpaEntity e = entity(id, UUID.randomUUID(), "NEW");
        e.setSocialLinksJson("not-json");
        when(jpaRepository.findById(id)).thenReturn(Optional.of(e));

        assertThat(adapter.findById(id).get().getSocialLinks()).isEmpty();
    }
}
