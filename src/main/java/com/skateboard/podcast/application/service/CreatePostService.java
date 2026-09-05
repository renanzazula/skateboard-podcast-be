package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.CreatePostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostPlatform;
import com.skateboard.podcast.domain.model.PostPlatformLink;
import org.springframework.stereotype.Service;

/**
 * Creating a post does not announce it. A saved post that is PUBLISHED, recent
 * and carries no {@code notifiedAt} <em>is</em> the record that an announcement
 * is owed, and PendingPodcastNotificationJob is the single thing that acts on
 * it — see PodcastPublicationNotifier.
 *
 * <p>That keeps the broker out of this request entirely: creating an episode is
 * a database write, so RabbitMQ being down delays a notification instead of
 * putting a remote call in the admin's path.
 */
@Service
public class CreatePostService implements CreatePostUseCase {

    private final LoadPostPort loadPostPort;
    private final SavePostPort savePostPort;

    public CreatePostService(LoadPostPort loadPostPort, SavePostPort savePostPort) {
        this.loadPostPort = loadPostPort;
        this.savePostPort = savePostPort;
    }

    @Override
    public Post execute(Input input) {
        String slug = ensureUniqueSlug(input.slug());
        Post post = Post.create(input.title(), slug, input.status(), input.publishAt(),
                input.coverUrl(), input.blocksJson(), input.socialMediaLinksJson(), input.createdBy());
        post.attachCoverDimensions(input.coverWidth(), input.coverHeight());
        if (input.youtubeVideoId() != null) {
            post.attachYoutubeMetadata(input.youtubeVideoId(), input.description(),
                    input.durationSeconds(), input.episodeNumber());
            post.attachPlatformLink(new PostPlatformLink(PostPlatform.YOUTUBE, input.youtubeVideoId(),
                    "https://www.youtube.com/watch?v=" + input.youtubeVideoId()));
        }
        return savePostPort.save(post);
    }

    private String ensureUniqueSlug(String base) {
        String slug = base;
        int counter = 1;
        while (loadPostPort.existsBySlug(slug)) {
            slug = base + "-" + counter++;
        }
        return slug;
    }
}
