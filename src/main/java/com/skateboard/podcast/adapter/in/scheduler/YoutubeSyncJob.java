package com.skateboard.podcast.adapter.in.scheduler;

import com.skateboard.podcast.application.port.in.SynchronizeYoutubeChannelUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the use case only — no HTTP/persistence logic here. Locked via
 * ShedLock so at most one instance runs a cycle when multiple instances are
 * deployed (see infrastructure.youtube.YoutubeSchedulerLockConfig).
 */
@Component
@ConditionalOnProperty(prefix = "youtube.sync", name = "enabled", havingValue = "true")
public class YoutubeSyncJob {

    private final SynchronizeYoutubeChannelUseCase synchronizeYoutubeChannelUseCase;

    public YoutubeSyncJob(SynchronizeYoutubeChannelUseCase synchronizeYoutubeChannelUseCase) {
        this.synchronizeYoutubeChannelUseCase = synchronizeYoutubeChannelUseCase;
    }

    @Scheduled(cron = "${youtube.sync.cron}")
    @SchedulerLock(name = "youtubeSync", lockAtMostFor = "9m")
    public void run() {
        synchronizeYoutubeChannelUseCase.execute();
    }
}
