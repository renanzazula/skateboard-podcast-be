package com.skateboard.podcast.application.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the show's episode number from a video/episode title. Shared by
 * the YouTube sync and Spotify episode matching (both title conventions
 * follow "... #24" / "EP 24 ...", per .docs/README_SPOTIFY_YOUTUBE_PODCAST_INTEGRATION.md §12).
 */
public final class EpisodeNumberParser {

    // Same convention FE's episodeMeta.ts uses for the show's own episode numbering.
    private static final Pattern TRAILING_HASH_NUMBER = Pattern.compile("#(\\d+)\\s*$");
    private static final Pattern LEADING_EP_NUMBER = Pattern.compile("^\\s*ep\\.?\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private EpisodeNumberParser() {}

    public static Integer parse(String title) {
        if (title == null) return null;
        String trimmed = title.trim();
        Matcher trailing = TRAILING_HASH_NUMBER.matcher(trimmed);
        if (trailing.find()) return Integer.parseInt(trailing.group(1));
        Matcher leading = LEADING_EP_NUMBER.matcher(trimmed);
        if (leading.find()) return Integer.parseInt(leading.group(1));
        return null;
    }
}
