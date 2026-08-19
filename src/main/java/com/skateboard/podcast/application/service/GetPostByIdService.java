package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetPostByIdUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.domain.model.Post;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GetPostByIdService implements GetPostByIdUseCase {

    private final LoadPostPort loadPostPort;

    public GetPostByIdService(LoadPostPort loadPostPort) {
        this.loadPostPort = loadPostPort;
    }

    @Override
    public Optional<Post> execute(String id) {
        return loadPostPort.findById(id);
    }
}
