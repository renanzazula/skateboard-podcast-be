package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetPostsByCategoryUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.application.port.out.PostCategoryPort;
import com.skateboard.podcast.domain.exception.CategoryNotFoundException;
import com.skateboard.podcast.domain.model.Post;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetPostsByCategoryService implements GetPostsByCategoryUseCase {

    private final CategoryRepositoryPort categoryRepositoryPort;
    private final PostCategoryPort postCategoryPort;

    public GetPostsByCategoryService(CategoryRepositoryPort categoryRepositoryPort, PostCategoryPort postCategoryPort) {
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.postCategoryPort = postCategoryPort;
    }

    @Override
    public Result execute(String slug, int page, int size) {
        categoryRepositoryPort.findBySlug(slug)
                .filter(c -> c.isEnabled())
                .orElseThrow(() -> new CategoryNotFoundException(slug));

        List<Post> posts = postCategoryPort.findPublishedByCategorySlug(slug, page, size);
        long total = postCategoryPort.countPublishedByCategorySlug(slug);
        return new Result(posts, total);
    }
}
