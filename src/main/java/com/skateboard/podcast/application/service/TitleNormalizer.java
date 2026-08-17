package com.skateboard.podcast.application.service;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Normalizes an episode title for cross-platform comparison
 * (.docs/README_SPOTIFY_YOUTUBE_PODCAST_INTEGRATION.md §12.2): strips a
 * trailing "| Channel Name" suffix, removes accents/punctuation, lowercases,
 * and collapses whitespace.
 */
public final class TitleNormalizer {

    private static final Pattern CHANNEL_SUFFIX = Pattern.compile("\\s*\\|.*$");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TitleNormalizer() {}

    public static String normalize(String title) {
        if (title == null) return "";
        String withoutSuffix = CHANNEL_SUFFIX.matcher(title).replaceAll("");
        String withoutAccents = Normalizer.normalize(withoutSuffix, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String lower = withoutAccents.toLowerCase();
        String withoutPunctuation = NON_ALPHANUMERIC.matcher(lower).replaceAll(" ");
        return WHITESPACE.matcher(withoutPunctuation).replaceAll(" ").trim();
    }
}
