package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.UpdatePostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.exception.PostNotFoundException;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UpdatePostService implements UpdatePostUseCase {

    private final LoadPostPort loadPostPort;
    private final SavePostPort savePostPort;
    private final PodcastPublicationNotifier publicationNotifier;

    public UpdatePostService(LoadPostPort loadPostPort, SavePostPort savePostPort,
                             PodcastPublicationNotifier publicationNotifier) {
        this.loadPostPort = loadPostPort;
        this.savePostPort = savePostPort;
        this.publicationNotifier = publicationNotifier;
    }

    @Override
    public Post execute(Input input) {
        Post post = loadPostPort.findById(input.id())
                .orElseThrow(() -> new PostNotFoundException(input.id()));
        // Captured before the update so a genuine DRAFT/SCHEDULED -> PUBLISHED
        // transition can be told apart from editing a post that was already
        // published. Only the former is news.
        PostStatus previousStatus = post.getStatus();
        PostStatus status = input.status() != null ? input.status() : post.getStatus();
        // A PUT that omits publishAt means "leave as-is" — the editor has no
        // date field, and nulling publishAt silently re-dates the episode and
        // reshuffles the feed (ordered by COALESCE(publishAt, createdAt)).
        Instant publishAt = input.publishAt() != null ? input.publishAt() : post.getPublishAt();
        post.update(input.title(), input.slug(), status, publishAt,
                input.coverUrl(), input.blocksJson(), input.socialMediaLinksJson());
        Post saved = savePostPort.save(post);
        if (previousStatus != PostStatus.PUBLISHED) {
            publicationNotifier.notifyIfNewlyPublished(saved);
        }
        return saved;
    }
}
