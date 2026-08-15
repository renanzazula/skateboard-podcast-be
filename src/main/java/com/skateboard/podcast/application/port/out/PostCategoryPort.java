package com.skateboard.podcast.application.port.out;

import com.skateboard.podcast.domain.model.Post;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface PostCategoryPort {
    /** youtube_video_id set of every post currently associated with the category — used to diff against YouTube. */
    Set<String> findVideoIdsByCategory(UUID categoryId);
    void addAssociation(UUID postId, UUID categoryId);
    void removeAssociation(UUID postId, UUID categoryId);
    /** Published posts in the category, ordered COALESCE(publishAt, createdAt) DESC. */
    List<Post> findPublishedByCategorySlug(String slug, int page, int size);
    long countPublishedByCategorySlug(String slug);
    /** Published post count per category id, for GetCategoriesUseCase. */
    Map<UUID, Long> countPublishedByCategory();
}
