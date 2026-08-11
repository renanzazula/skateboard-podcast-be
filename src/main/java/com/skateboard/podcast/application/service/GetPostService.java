package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetPostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.domain.model.Post;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetPostService implements GetPostUseCase {

    private final LoadPostPort loadPostPort;

    public GetPostService(LoadPostPort loadPostPort) {
        this.loadPostPort = loadPostPort;
    }

    @Override
    public Result execute(int page, int size) {
        List<Post> posts = loadPostPort.findPublished(page, size);
        long total = loadPostPort.countPublished();
        return new Result(posts, total);
    }
}
