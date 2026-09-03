package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.CreatePostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostPlatform;
import com.skateboard.podcast.domain.model.PostPlatformLink;
import org.springframework.stereotype.Service;

@Service
public class CreatePostService implements CreatePostUseCase {

    private final LoadPostPort loadPostPort;
    private final SavePostPort savePostPort;
    private final PodcastPublicationNotifier publicationNotifier;

    public CreatePostService(LoadPostPort loadPostPort, SavePostPort savePostPort,
                             PodcastPublicationNotifier publicationNotifier) {
        this.loadPostPort = loadPostPort;
        this.savePostPort = savePostPort;
        this.publicationNotifier = publicationNotifier;
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
        Post saved = savePostPort.save(post);
        // Every create path funnels through here — manual authoring, the JSON
        // import and the YouTube sync — so this is the one place that has to
        // announce a new episode. The notifier decides whether it qualifies;
        // the back catalogue the sync ingests does not.
        publicationNotifier.notifyIfNewlyPublished(saved);
        return saved;
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
