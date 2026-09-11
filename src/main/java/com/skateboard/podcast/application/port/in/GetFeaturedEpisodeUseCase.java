package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Post;

import java.util.Optional;

/**
 * The latest published, YouTube-sourced episode matching the show's official
 * numbering convention ("Skateboard Podcast #&lt;n&gt;") — the candidate the
 * Home Featured Player's AUTO selection mode resolves to. See
 * {@link com.skateboard.podcast.application.service.GetFeaturedEpisodeService}.
 */
public interface GetFeaturedEpisodeUseCase {
    Optional<Post> execute();
}
