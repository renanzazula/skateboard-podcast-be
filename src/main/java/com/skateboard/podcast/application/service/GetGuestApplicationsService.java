package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetGuestApplicationsUseCase;
import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import org.springframework.stereotype.Service;

@Service
public class GetGuestApplicationsService implements GetGuestApplicationsUseCase {

    private final LoadGuestApplicationPort loadGuestApplicationPort;

    public GetGuestApplicationsService(LoadGuestApplicationPort loadGuestApplicationPort) {
        this.loadGuestApplicationPort = loadGuestApplicationPort;
    }

    @Override
    public Result execute(Input input) {
        return new Result(
                loadGuestApplicationPort.findAll(input.status(), input.page(), input.size()),
                loadGuestApplicationPort.countAll(input.status()));
    }
}
