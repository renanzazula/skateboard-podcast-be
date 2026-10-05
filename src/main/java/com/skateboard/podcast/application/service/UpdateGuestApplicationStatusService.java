package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.UpdateGuestApplicationStatusUseCase;
import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.application.port.out.SaveGuestApplicationPort;
import com.skateboard.podcast.domain.exception.GuestApplicationNotFoundException;
import com.skateboard.podcast.domain.model.GuestApplication;
import org.springframework.stereotype.Service;

@Service
public class UpdateGuestApplicationStatusService implements UpdateGuestApplicationStatusUseCase {

    private final LoadGuestApplicationPort loadGuestApplicationPort;
    private final SaveGuestApplicationPort saveGuestApplicationPort;

    public UpdateGuestApplicationStatusService(LoadGuestApplicationPort loadGuestApplicationPort,
                                               SaveGuestApplicationPort saveGuestApplicationPort) {
        this.loadGuestApplicationPort = loadGuestApplicationPort;
        this.saveGuestApplicationPort = saveGuestApplicationPort;
    }

    @Override
    public GuestApplication execute(Input input) {
        GuestApplication application = loadGuestApplicationPort.findById(input.id())
                .orElseThrow(() -> new GuestApplicationNotFoundException(String.valueOf(input.id())));
        application.updateStatus(input.status(), input.reviewedBy());
        return saveGuestApplicationPort.save(application);
    }
}
