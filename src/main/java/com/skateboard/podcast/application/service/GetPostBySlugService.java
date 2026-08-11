package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetPostBySlugUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.domain.model.Post;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GetPostBySlugService implements GetPostBySlugUseCase {

    private final LoadPostPort loadPostPort;

    public GetPostBySlugService(LoadPostPort loadPostPort) {
        this.loadPostPort = loadPostPort;
    }

    @Override
    public Optional<Post> execute(String slug) {
        return loadPostPort.findBySlug(slug);
    }
}
