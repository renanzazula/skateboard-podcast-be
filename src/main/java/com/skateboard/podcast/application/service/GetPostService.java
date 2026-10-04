package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetPostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.domain.model.EpisodeSearch;
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
    public Result execute(String search, int page, int size) {
        EpisodeSearch query = EpisodeSearch.parse(search);
        if (query == null) {
            List<Post> posts = loadPostPort.findPublished(page, size);
            long total = loadPostPort.countPublished();
            return new Result(posts, total);
        }
        List<Post> posts = loadPostPort.searchPublished(query, page, size);
        long total = loadPostPort.countSearchPublished(query);
        return new Result(posts, total);
    }
}
