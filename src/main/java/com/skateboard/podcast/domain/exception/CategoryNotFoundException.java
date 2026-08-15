package com.skateboard.podcast.domain.exception;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String slug) {
        super("Category not found: " + slug);
    }
}
