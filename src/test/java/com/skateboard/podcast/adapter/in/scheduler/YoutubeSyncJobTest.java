package com.skateboard.podcast.adapter.in.scheduler;

import com.skateboard.podcast.application.port.in.SynchronizeYoutubeChannelUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Thin @Scheduled wrapper — no HTTP/persistence logic of its own (see
 * CLAUDE.md, adapter/in/scheduler). All this class is responsible for is
 * triggering the use case exactly once per run.
 */
@ExtendWith(MockitoExtension.class)
class YoutubeSyncJobTest {

    @Mock
    private SynchronizeYoutubeChannelUseCase synchronizeYoutubeChannelUseCase;

    @Test
    void triggersTheYoutubeSyncUseCaseOnEachRun() {
        YoutubeSyncJob job = new YoutubeSyncJob(synchronizeYoutubeChannelUseCase);

        job.run();

        verify(synchronizeYoutubeChannelUseCase).execute();
        verifyNoMoreInteractions(synchronizeYoutubeChannelUseCase);
    }

    @Test
    void triggersTheUseCaseAgainOnASecondRun() {
        YoutubeSyncJob job = new YoutubeSyncJob(synchronizeYoutubeChannelUseCase);

        job.run();
        job.run();

        verify(synchronizeYoutubeChannelUseCase, org.mockito.Mockito.times(2)).execute();
    }
}
