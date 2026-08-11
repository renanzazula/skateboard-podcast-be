package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.UpdatePostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.exception.PostNotFoundException;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.springframework.stereotype.Service;

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
        post.update(input.title(), input.slug(), status, input.publishAt(),
                input.coverUrl(), input.blocksJson(), input.socialMediaLinksJson());
        return savePostPort.save(post);
    }
}
