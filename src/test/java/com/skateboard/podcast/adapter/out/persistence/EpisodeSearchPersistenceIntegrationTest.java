package com.skateboard.podcast.adapter.out.persistence;

import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.PostCategoryPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Category;
import com.skateboard.podcast.domain.model.EpisodeSearch;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres (Testcontainers) behind the full Flyway stack — runs the
 * episode-search JPQL (LIKE ... ESCAPE, the CASE ordering, the explicit count
 * queries) for real, which the mocked adapter tests can't.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/realms/test",
        "app.security.oauth2.audience=skateboard-podcast-be"
})
@Testcontainers
class EpisodeSearchPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private LoadPostPort loadPostPort;
    @Autowired private SavePostPort savePostPort;
    @Autowired private CategoryRepositoryPort categoryRepositoryPort;
    @Autowired private PostCategoryPort postCategoryPort;

    private final Instant now = Instant.now();
    private String run;
    private String slug;

    @BeforeEach
    void seed() {
        run = UUID.randomUUID().toString().substring(0, 8);
        slug = "search-" + run;
        Category category = categoryRepositoryPort.save(
                Category.createFromYoutube(slug, "PL-" + run, "Search " + run, null, null, false));

        inCategory(category, publish("Skateboard Podcast #42", 42, 10));
        inCategory(category, publish("Skateboard Podcast #142", 142, 1));
        inCategory(category, publish("Interview 42 tricks", null, 2));
        inCategory(category, publish("100% Skate", null, 3));
        inCategory(category, publish("100 Skate", null, 4));
        inCategory(category, save("Skateboard Podcast #43", 43, 5, PostStatus.DRAFT));
        publish("Skateboard Podcast #44 " + run, 44, 6); // published, but not in the category
    }

    @Test
    void exactEpisodeNumberRanksFirstThenTitleMatchesByPublishDate() {
        assertThat(titles(categorySearch("42", 0, 10)))
                .containsExactly("Skateboard Podcast #42", "Skateboard Podcast #142", "Interview 42 tricks");
        assertThat(postCategoryPort.countSearchPublishedByCategorySlug(slug, EpisodeSearch.parse("42"))).isEqualTo(3);
    }

    @Test
    void titleMatchIsCaseInsensitiveAndPartialAndSkipsDrafts() {
        assertThat(titles(categorySearch("SKATEBOARD podcast", 0, 10)))
                .containsExactly("Skateboard Podcast #142", "Skateboard Podcast #42");
    }

    @Test
    void episodePrefixMatchesTheNumberEvenThoughNoTitleContainsIt() {
        assertThat(titles(categorySearch("episode 42", 0, 10))).containsExactly("Skateboard Podcast #42");
    }

    @Test
    void likeWildcardsInTheSearchMatchLiterally() {
        assertThat(titles(categorySearch("100%", 0, 10))).containsExactly("100% Skate");
    }

    @Test
    void searchResultsPageWithTheSameOrdering() {
        assertThat(titles(categorySearch("42", 0, 2)))
                .containsExactly("Skateboard Podcast #42", "Skateboard Podcast #142");
        assertThat(titles(categorySearch("42", 1, 2))).containsExactly("Interview 42 tricks");
    }

    @Test
    void categorySearchStaysInsideTheCategoryWhileTheGlobalFeedDoesNot() {
        EpisodeSearch search = EpisodeSearch.parse("#44 " + run);
        assertThat(postCategoryPort.searchPublishedByCategorySlug(slug, search, 0, 10)).isEmpty();
        assertThat(titles(loadPostPort.searchPublished(search, 0, 10))).containsExactly("Skateboard Podcast #44 " + run);
        assertThat(loadPostPort.countSearchPublished(search)).isEqualTo(1);
    }

    private List<Post> categorySearch(String raw, int page, int size) {
        return postCategoryPort.searchPublishedByCategorySlug(slug, EpisodeSearch.parse(raw), page, size);
    }

    private Post publish(String title, Integer episodeNumber, int daysAgo) {
        return save(title, episodeNumber, daysAgo, PostStatus.PUBLISHED);
    }

    private Post save(String title, Integer episodeNumber, int daysAgo, PostStatus status) {
        Post post = Post.create(title, run + "-" + UUID.randomUUID(), status,
                now.minus(Duration.ofDays(daysAgo)), null, "[]", "[]", null);
        if (episodeNumber != null) {
            // youtube_video_id is varchar(20), like a real 11-char video id.
            post.attachYoutubeMetadata(UUID.randomUUID().toString().substring(0, 11), null, null, episodeNumber);
        }
        return savePostPort.save(post);
    }

    private void inCategory(Category category, Post post) {
        postCategoryPort.addAssociation(post.getId(), category.getId());
    }

    private static List<String> titles(List<Post> posts) {
        return posts.stream().map(Post::getTitle).toList();
    }
}
