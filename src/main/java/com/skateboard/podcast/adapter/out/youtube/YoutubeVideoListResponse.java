package com.skateboard.podcast.adapter.out.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record YoutubeVideoListResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String id, ContentDetails contentDetails) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentDetails(String duration) {}
}
