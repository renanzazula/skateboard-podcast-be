package com.skateboard.podcast;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class PodcastApplicationTest {

    @Test
    void mainDelegatesToSpringApplicationRun() {
        String[] args = {"--server.port=0"};
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            PodcastApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(PodcastApplication.class, args));
        }
    }
}
