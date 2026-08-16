package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.CreatePostUseCase;
import com.skateboard.podcast.application.port.in.SynchronizeYoutubeChannelUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.PostCategoryPort;
import com.skateboard.podcast.application.port.out.YoutubeContentPort;
import com.skateboard.podcast.domain.model.Category;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import com.skateboard.podcast.infrastructure.youtube.YoutubeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SynchronizeYoutubeChannelServiceTest {

    @Mock private YoutubeContentPort youtubeContentPort;
    @Mock private LoadPostPort loadPostPort;
    @Mock private CreatePostUseCase createPostUseCase;
    @Mock private CategoryRepositoryPort categoryRepositoryPort;
    @Mock private PostCategoryPort postCategoryPort;

    private YoutubeProperties properties;
    private SynchronizeYoutubeChannelService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new YoutubeProperties();
        properties.setChannelId("UC_TEST_CHANNEL");
        properties.getSync().setInitialImportLimit(20);
        service = new SynchronizeYoutubeChannelService(youtubeContentPort, loadPostPort, createPostUseCase,
                categoryRepositoryPort, postCategoryPort, properties);

        // Defaults so tests that only care about the uploads catch-all (or
        // only about playlists) don't have to stub the other side.
        when(youtubeContentPort.getPlaylists(anyString())).thenReturn(List.of());
        when(youtubeContentPort.getLatestVideos(anyString(), anyInt())).thenReturn(List.of());
        when(categoryRepositoryPort.findAll()).thenReturn(List.of());
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepositoryPort.findBySlug(anyString())).thenReturn(Optional.empty());
        when(postCategoryPort.findVideoIdsByCategory(any())).thenReturn(Set.of());
    }

    private YoutubeContentPort.YoutubeVideo video(String id, String title) {
        return new YoutubeContentPort.YoutubeVideo(id, title, "desc " + id, Instant.parse("2026-01-01T00:00:00Z"), "http://thumb/" + id);
    }

    private YoutubeContentPort.YoutubePlaylist playlist(String id, String title) {
        return new YoutubeContentPort.YoutubePlaylist(id, title, "playlist desc " + id, "http://thumb/playlist/" + id);
    }

    private void stubChannelResolution() {
        when(youtubeContentPort.resolveChannel("UC_TEST_CHANNEL"))
                .thenReturn(new YoutubeContentPort.YoutubeChannel("UC_TEST_CHANNEL", "Show", "UU_TEST_UPLOADS"));
    }

    // A real (lightweight) Post rather than a Mockito mock — Post has no
    // behavior worth stubbing here, and mocking concrete classes needs the
    // inline mock maker's bytecode instrumentation, which is unnecessary risk
    // for a value the test never calls a method on.
    private Post somePost() {
        return Post.create("Some Post", "some-post", PostStatus.PUBLISHED, null, null, "[]", "[]", null);
    }

    // ── Uploads catch-all (pre-existing behavior) ───────────────────────────

    @Test
    void newVideoIsPersisted() {
        stubChannelResolution();
        when(youtubeContentPort.getLatestVideos("UU_TEST_UPLOADS", 20)).thenReturn(List.of(video("v1", "Episode #1")));
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.empty());
        when(youtubeContentPort.getVideoDurations(List.of("v1")))
                .thenReturn(List.of(new YoutubeContentPort.YoutubeVideoDuration("v1", 120)));
        when(createPostUseCase.execute(any())).thenReturn(somePost());

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.success()).isTrue();
        assertThat(result.received()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.existing()).isEqualTo(0);

        ArgumentCaptor<CreatePostUseCase.Input> captor = ArgumentCaptor.forClass(CreatePostUseCase.Input.class);
        verify(createPostUseCase).execute(captor.capture());
        CreatePostUseCase.Input input = captor.getValue();
        assertThat(input.youtubeVideoId()).isEqualTo("v1");
        assertThat(input.description()).isEqualTo("desc v1");
        assertThat(input.durationSeconds()).isEqualTo(120);
        assertThat(input.episodeNumber()).isEqualTo(1);
        assertThat(input.status()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void existingVideoIsNotDuplicated() {
        stubChannelResolution();
        when(youtubeContentPort.getLatestVideos("UU_TEST_UPLOADS", 20)).thenReturn(List.of(video("v1", "Episode #1")));
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.of(somePost()));

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.existing()).isEqualTo(1);
        verifyNoInteractions(createPostUseCase);
    }

    @Test
    void multipleNewVideosAreAllPersisted() {
        stubChannelResolution();
        when(youtubeContentPort.getLatestVideos("UU_TEST_UPLOADS", 20))
                .thenReturn(List.of(video("v1", "Ep #1"), video("v2", "Ep #2")));
        when(loadPostPort.findByYoutubeVideoId(anyString())).thenReturn(Optional.empty());
        when(youtubeContentPort.getVideoDurations(any())).thenReturn(List.of());
        when(createPostUseCase.execute(any())).thenReturn(somePost());

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.created()).isEqualTo(2);
        verify(createPostUseCase, times(2)).execute(any());
    }

    @Test
    void disabledChannelIsSkipped() {
        properties.setChannelId(null);

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.success()).isFalse();
        assertThat(result.received()).isEqualTo(0);
        verifyNoInteractions(youtubeContentPort, createPostUseCase);
    }

    @Test
    void youtubeFailureIsHandledWithoutThrowing() {
        when(youtubeContentPort.resolveChannel("UC_TEST_CHANNEL"))
                .thenThrow(new YoutubeContentPort.YoutubeSyncException("boom"));

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.success()).isFalse();
        verifyNoInteractions(createPostUseCase);
    }

    @Test
    void oneVideoFailureDoesNotBlockTheRest() {
        stubChannelResolution();
        when(youtubeContentPort.getLatestVideos("UU_TEST_UPLOADS", 20))
                .thenReturn(List.of(video("bad", "Bad Episode"), video("good", "Good Episode")));
        when(loadPostPort.findByYoutubeVideoId(anyString())).thenReturn(Optional.empty());
        when(youtubeContentPort.getVideoDurations(any())).thenReturn(List.of());
        when(createPostUseCase.execute(any()))
                .thenThrow(new RuntimeException("slug collision"))
                .thenReturn(somePost());

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.created()).isEqualTo(1);
        verify(createPostUseCase, times(2)).execute(any());
    }

    // ── Playlists -> categories ──────────────────────────────────────────────

    @Test
    void newPlaylistCreatesCategory() {
        stubChannelResolution();
        when(youtubeContentPort.getPlaylists("UC_TEST_CHANNEL")).thenReturn(List.of(playlist("PL1", "Podcasts")));
        when(categoryRepositoryPort.findByExternalId("YOUTUBE", "PL1")).thenReturn(Optional.empty());
        when(youtubeContentPort.getAllPlaylistItems("PL1")).thenReturn(List.of());

        service.execute();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepositoryPort).save(captor.capture());
        Category saved = captor.getValue();
        assertThat(saved.getExternalId()).isEqualTo("PL1");
        assertThat(saved.getSlug()).isEqualTo("podcasts");
        assertThat(saved.getName()).isEqualTo("Podcasts");
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void existingPlaylistUpdatesCategoryWithoutDuplicating() {
        stubChannelResolution();
        when(youtubeContentPort.getPlaylists("UC_TEST_CHANNEL")).thenReturn(List.of(playlist("PL1", "Podcasts Renamed")));
        Category existing = Category.createFromYoutube("podcasts", "PL1", "Podcasts", "old desc", "old-cover", false);
        when(categoryRepositoryPort.findByExternalId("YOUTUBE", "PL1")).thenReturn(Optional.of(existing));
        when(youtubeContentPort.getAllPlaylistItems("PL1")).thenReturn(List.of());

        service.execute();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepositoryPort).save(captor.capture());
        Category saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(existing.getId());
        assertThat(saved.getSlug()).isEqualTo("podcasts"); // slug stays stable across a rename
        assertThat(saved.getName()).isEqualTo("Podcasts Renamed");
        verify(categoryRepositoryPort, never()).findBySlug(anyString());
    }

    @Test
    void defaultPlaylistMarksCategoryAsDefault() {
        properties.setDefaultPlaylistId("PL1");
        stubChannelResolution();
        when(youtubeContentPort.getPlaylists("UC_TEST_CHANNEL"))
                .thenReturn(List.of(playlist("PL1", "Podcasts"), playlist("PL2", "Events")));
        when(categoryRepositoryPort.findByExternalId(anyString(), anyString())).thenReturn(Optional.empty());
        when(youtubeContentPort.getAllPlaylistItems(anyString())).thenReturn(List.of());

        service.execute();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepositoryPort, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(c -> c.getExternalId().equals("PL1")).singleElement()
                .satisfies(c -> assertThat(c.isDefault()).isTrue());
        assertThat(captor.getAllValues())
                .filteredOn(c -> c.getExternalId().equals("PL2")).singleElement()
                .satisfies(c -> assertThat(c.isDefault()).isFalse());
    }

    @Test
    void adminRenameAndDefaultSurviveTheSync() {
        // Admin renamed the category and picked it as default (which locks
        // defaulting); config points at a different playlist entirely.
        properties.setDefaultPlaylistId("PL_OTHER");
        stubChannelResolution();
        when(youtubeContentPort.getPlaylists("UC_TEST_CHANNEL")).thenReturn(List.of(playlist("PL1", "Podcasts Renamed")));
        Category existing = Category.createFromYoutube("podcasts", "PL1", "Podcasts", "old desc", "old-cover", false);
        existing.rename("My Interviews");
        existing.markDefault();
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(existing));
        when(categoryRepositoryPort.findByExternalId("YOUTUBE", "PL1")).thenReturn(Optional.of(existing));
        when(youtubeContentPort.getAllPlaylistItems("PL1")).thenReturn(List.of());

        service.execute();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepositoryPort).save(captor.capture());
        Category saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Podcasts Renamed"); // YouTube's half keeps refreshing
        assertThat(saved.getCustomName()).isEqualTo("My Interviews");
        assertThat(saved.getEffectiveName()).isEqualTo("My Interviews");
        assertThat(saved.isDefault()).isTrue(); // config no longer applies once locked
    }

    @Test
    void newPlaylistIsNotDefaultedWhileDefaultIsAdminOwned() {
        // Config points at the new playlist, but an admin already picked a
        // default elsewhere — the new category must not become a second default.
        properties.setDefaultPlaylistId("PL_NEW");
        stubChannelResolution();
        when(youtubeContentPort.getPlaylists("UC_TEST_CHANNEL"))
                .thenReturn(List.of(playlist("PL_NEW", "New Playlist"), playlist("PL1", "Podcasts")));
        Category adminDefault = Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, false);
        adminDefault.markDefault();
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(adminDefault));
        when(categoryRepositoryPort.findByExternalId("YOUTUBE", "PL_NEW")).thenReturn(Optional.empty());
        when(categoryRepositoryPort.findByExternalId("YOUTUBE", "PL1")).thenReturn(Optional.of(adminDefault));
        when(youtubeContentPort.getAllPlaylistItems(anyString())).thenReturn(List.of());

        service.execute();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepositoryPort, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(c -> c.getExternalId().equals("PL_NEW")).singleElement()
                .satisfies(c -> assertThat(c.isDefault()).isFalse());
        // The admin-picked default is untouched by the config change.
        assertThat(adminDefault.isDefault()).isTrue();
    }

    @Test
    void missingPlaylistDisablesCategory() {
        stubChannelResolution();
        when(youtubeContentPort.getPlaylists("UC_TEST_CHANNEL")).thenReturn(List.of());
        Category stale = Category.createFromYoutube("events", "PL_GONE", "Events", null, null, false);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(stale));

        service.execute();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepositoryPort).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
    }

    @Test
    void videoInTwoPlaylistsGetsTwoAssociations() {
        stubChannelResolution();
        when(youtubeContentPort.getPlaylists("UC_TEST_CHANNEL"))
                .thenReturn(List.of(playlist("PL1", "Podcasts"), playlist("PL2", "Events")));
        when(categoryRepositoryPort.findByExternalId(anyString(), anyString())).thenReturn(Optional.empty());
        when(youtubeContentPort.getAllPlaylistItems(anyString())).thenReturn(List.of(video("v1", "Ep #1")));
        Post created = somePost();
        // First playlist: not found yet, gets created. Second playlist: now found, reused.
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.empty(), Optional.of(created));
        when(createPostUseCase.execute(any())).thenReturn(created);

        service.execute();

        verify(createPostUseCase, times(1)).execute(any());
        verify(postCategoryPort, times(2)).addAssociation(any(), any());
    }

    @Test
    void videoRemovedFromPlaylistRemovesOnlyThatAssociationAndKeepsPost() {
        stubChannelResolution();
        when(youtubeContentPort.getPlaylists("UC_TEST_CHANNEL")).thenReturn(List.of(playlist("PL1", "Podcasts")));
        Category category = Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, false);
        when(categoryRepositoryPort.findByExternalId("YOUTUBE", "PL1")).thenReturn(Optional.of(category));
        when(youtubeContentPort.getAllPlaylistItems("PL1")).thenReturn(List.of()); // video no longer in the playlist
        when(postCategoryPort.findVideoIdsByCategory(category.getId())).thenReturn(Set.of("v1"));
        Post existingPost = somePost();
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.of(existingPost));

        service.execute();

        verify(postCategoryPort).removeAssociation(existingPost.getId(), category.getId());
        verifyNoInteractions(createPostUseCase); // the post itself is never deleted/recreated
    }

    @Test
    void runningSyncTwiceDoesNotDuplicateCategoriesOrAssociations() {
        stubChannelResolution();
        when(youtubeContentPort.getPlaylists("UC_TEST_CHANNEL")).thenReturn(List.of(playlist("PL1", "Podcasts")));
        when(youtubeContentPort.getAllPlaylistItems("PL1")).thenReturn(List.of(video("v1", "Ep #1")));

        // First run: playlist and video are new.
        when(categoryRepositoryPort.findByExternalId("YOUTUBE", "PL1")).thenReturn(Optional.empty());
        Category savedCategory = Category.createFromYoutube("podcasts", "PL1", "Podcasts", "d", "c", false);
        when(categoryRepositoryPort.save(any())).thenReturn(savedCategory);
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.empty());
        Post createdPost = somePost();
        when(createPostUseCase.execute(any())).thenReturn(createdPost);

        service.execute();
        verify(createPostUseCase, times(1)).execute(any());
        verify(postCategoryPort, times(1)).addAssociation(createdPost.getId(), savedCategory.getId());

        // Second run: everything already exists — idempotent, no new creation/association.
        when(categoryRepositoryPort.findByExternalId("YOUTUBE", "PL1")).thenReturn(Optional.of(savedCategory));
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.of(createdPost));
        when(postCategoryPort.findVideoIdsByCategory(savedCategory.getId())).thenReturn(Set.of("v1"));

        service.execute();

        verify(createPostUseCase, times(1)).execute(any()); // still just the one call from run 1
        verify(postCategoryPort, times(1)).addAssociation(any(), any()); // still just the one call from run 1
    }
}
