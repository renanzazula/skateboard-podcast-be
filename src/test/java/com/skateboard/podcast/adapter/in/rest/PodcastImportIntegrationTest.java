package com.skateboard.podcast.adapter.in.rest;

import com.skateboard.podcast.adapter.out.persistence.SpringPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end import: a real Postgres (Testcontainers) behind the full
 * persistence/Flyway stack, driven through the actual
 * {@code POST /api/podcast/import} endpoint with the sample dataset in
 * skateboard_podcast_import_cleaned.json. Auth is a fake JWT injected via
 * spring-security-test rather than a real Keycloak container — SecurityConfig
 * only fetches JWKS lazily on first real decode (see its javadoc), so no
 * network call to Keycloak happens here at all.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/realms/test",
        "app.security.oauth2.audience=skateboard-podcast-be"
})
@AutoConfigureMockMvc
@Testcontainers
class PodcastImportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringPostRepository postRepository;

    @Test
    void importsEveryPostFromTheFixtureFile() throws Exception {
        String importJson = readFixture();

        mockMvc.perform(post("/api/podcast/import")
                        .with(jwt()
                                .jwt(j -> j.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("FUNC_PODCAST_IMPORT_JSON")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(importJson.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(89))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertThat(postRepository.count()).isEqualTo(89);
    }

    private String readFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/skateboard_podcast_import_cleaned.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
