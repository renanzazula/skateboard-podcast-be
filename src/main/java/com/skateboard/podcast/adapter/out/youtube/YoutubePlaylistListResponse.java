package com.skateboard.podcast.adapter.out.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
record YoutubePlaylistListResponse(List<Item> items, String nextPageToken) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String id, Snippet snippet) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Snippet(String title, String description, Map<String, YoutubePlaylistItemsResponse.Thumbnail> thumbnails) {}
}
