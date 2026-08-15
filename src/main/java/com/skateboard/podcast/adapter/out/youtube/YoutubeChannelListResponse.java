package com.skateboard.podcast.adapter.out.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record YoutubeChannelListResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String id, Snippet snippet, ContentDetails contentDetails) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Snippet(String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentDetails(RelatedPlaylists relatedPlaylists) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RelatedPlaylists(String uploads) {}
}
