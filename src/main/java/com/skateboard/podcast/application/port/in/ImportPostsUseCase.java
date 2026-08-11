package com.skateboard.podcast.application.port.in;

import java.util.List;
import java.util.UUID;

public interface ImportPostsUseCase {

    record PostImportItem(String title, String coverUrl, String status, String publishAt,
                           String blocksJson, String socialMediaLinksJson) {}
    record Input(List<PostImportItem> items, UUID importedBy) {}
    record Result(int imported, int failed, List<String> errors) {}

    Result execute(Input input);
}
