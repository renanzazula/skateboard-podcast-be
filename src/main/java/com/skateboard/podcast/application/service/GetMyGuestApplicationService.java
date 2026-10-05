package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetMyGuestApplicationUseCase;
import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.domain.model.GuestApplication;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class GetMyGuestApplicationService implements GetMyGuestApplicationUseCase {

    private final LoadGuestApplicationPort loadGuestApplicationPort;

    public GetMyGuestApplicationService(LoadGuestApplicationPort loadGuestApplicationPort) {
        this.loadGuestApplicationPort = loadGuestApplicationPort;
    }

    @Override
    public Optional<GuestApplication> execute(UUID userId) {
        return loadGuestApplicationPort.findActiveByUserId(userId)
                .or(() -> loadGuestApplicationPort.findLatestByUserId(userId));
    }
}
