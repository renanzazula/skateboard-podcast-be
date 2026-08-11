package com.skateboard.podcast.domain.exception;

public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException(String id) {
        super("Post not found: " + id);
    }
}
