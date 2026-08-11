package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.DeletePostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.exception.PostNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DeletePostService implements DeletePostUseCase {

    private final LoadPostPort loadPostPort;
    private final SavePostPort savePostPort;

    public DeletePostService(LoadPostPort loadPostPort, SavePostPort savePostPort) {
        this.loadPostPort = loadPostPort;
        this.savePostPort = savePostPort;
    }

    @Override
    public void execute(String id) {
        loadPostPort.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
        savePostPort.deleteById(id);
    }
}
