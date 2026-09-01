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

    // width/height come straight from the YouTube API's thumbnail objects —
    // the Home gallery sizes its masonry tiles from them (see V6 migration).
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Thumbnail(String url, Integer width, Integer height) {}

    // videoPublishedAt is the video's actual publication time (copied from the
    // video resource); snippet.publishedAt on a playlistItem is only when the
    // item was added to the playlist, so it must not be used as the publish date.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentDetails(String videoId, String videoPublishedAt) {}
}
