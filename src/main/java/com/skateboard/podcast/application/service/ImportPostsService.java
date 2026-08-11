package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.CreatePostUseCase;
import com.skateboard.podcast.application.port.in.ImportPostsUseCase;
import com.skateboard.podcast.domain.model.PostStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImportPostsService implements ImportPostsUseCase {

    private final CreatePostUseCase createPostUseCase;

    public ImportPostsService(CreatePostUseCase createPostUseCase) {
        this.createPostUseCase = createPostUseCase;
    }

    @Override
    public Result execute(Input input) {
        int imported = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (PostImportItem item : input.items()) {
            try {
                String slug = generateSlug(item.title());
                PostStatus status = parseStatus(item.status());
                Instant publishAt = item.publishAt() != null ? Instant.parse(item.publishAt()) : null;
                String blocksJson = item.blocksJson() != null ? item.blocksJson() : "[]";

                createPostUseCase.execute(new CreatePostUseCase.Input(
                        item.title(), slug, status, publishAt, item.coverUrl(), blocksJson,
                        item.socialMediaLinksJson(), input.importedBy()));
                imported++;
            } catch (Exception e) {
                failed++;
                errors.add("'" + item.title() + "': " + e.getMessage());
            }
        }

        return new Result(imported, failed, errors);
    }

    private String generateSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private PostStatus parseStatus(String s) {
        if (s == null) return PostStatus.PUBLISHED;
        try {
            return PostStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PostStatus.PUBLISHED;
        }
    }
}
