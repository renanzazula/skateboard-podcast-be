package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetGuestApplicationByIdUseCase;
import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.domain.model.GuestApplication;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class GetGuestApplicationByIdService implements GetGuestApplicationByIdUseCase {

    private final LoadGuestApplicationPort loadGuestApplicationPort;

    public GetGuestApplicationByIdService(LoadGuestApplicationPort loadGuestApplicationPort) {
        this.loadGuestApplicationPort = loadGuestApplicationPort;
    }

    @Override
    public Optional<GuestApplication> execute(UUID id) {
        return loadGuestApplicationPort.findById(id);
    }
}
