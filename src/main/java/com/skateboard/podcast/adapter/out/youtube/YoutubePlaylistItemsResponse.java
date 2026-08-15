package com.skateboard.podcast.adapter.out.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
record YoutubePlaylistItemsResponse(List<Item> items, String nextPageToken) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(Snippet snippet, ContentDetails contentDetails) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Snippet(String title, String description, String publishedAt, Map<String, Thumbnail> thumbnails) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Thumbnail(String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentDetails(String videoId) {}
}
