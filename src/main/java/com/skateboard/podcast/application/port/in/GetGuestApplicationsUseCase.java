package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.domain.model.GuestApplicationStatus;

import java.util.List;

public interface GetGuestApplicationsUseCase {

    record Input(GuestApplicationStatus status, int page, int size) {
    }

    record Result(List<GuestApplication> applications, long total) {
    }

    Result execute(Input input);
}
