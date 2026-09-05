package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.UpdatePostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.exception.PostNotFoundException;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Updating a post does not announce it, and needs no old-status comparison to
 * avoid announcing twice: {@code notifiedAt} already records whether this post
 * has been announced. An edit of a live episode has it set, so
 * PendingPodcastNotificationJob passes over the post; a draft going live has it
 * null, so the job picks the post up on its next pass.
 */
@Service
public class UpdatePostService implements UpdatePostUseCase {

    private final LoadPostPort loadPostPort;
    private final SavePostPort savePostPort;

    public UpdatePostService(LoadPostPort loadPostPort, SavePostPort savePostPort) {
        this.loadPostPort = loadPostPort;
        this.savePostPort = savePostPort;
    }

    @Override
    public Post execute(Input input) {
        Post post = loadPostPort.findById(input.id())
                .orElseThrow(() -> new PostNotFoundException(input.id()));
        PostStatus status = input.status() != null ? input.status() : post.getStatus();
        // A PUT that omits publishAt means "leave as-is" — the editor has no
        // date field, and nulling publishAt silently re-dates the episode and
        // reshuffles the feed (ordered by COALESCE(publishAt, createdAt)).
        Instant publishAt = input.publishAt() != null ? input.publishAt() : post.getPublishAt();
        post.update(input.title(), input.slug(), status, publishAt,
                input.coverUrl(), input.blocksJson(), input.socialMediaLinksJson());
        return savePostPort.save(post);
    }
}
