package com.skateboard.podcast.domain.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A listener's free-text episode search, parsed once so every feed that
 * filters on it agrees on what it means.
 *
 * <p>The text always matches as a case-insensitive "title contains". When the
 * whole input reads as an episode number — {@code 42}, {@code #42},
 * {@code ep 42}, {@code episode 42} — it additionally matches the post whose
 * {@code episodeNumber} is exactly that number, so "episode 42" finds #42 even
 * though no title contains those words, and that exact episode can be ranked
 * above titles that merely contain the digits (#142, #420).
 *
 * @param text          trimmed, lower-cased search text (never blank)
 * @param episodeNumber the episode number the input names, or {@code null}
 */
public record EpisodeSearch(String text, Integer episodeNumber) {

    /** Long enough for any real title; anything longer is not a search. */
    public static final int MAX_LENGTH = 100;

    private static final Pattern EPISODE_NUMBER =
            Pattern.compile("^(?:#|ep\\.?|episode)?\\s*#?\\s*(\\d{1,9})$", Pattern.CASE_INSENSITIVE);

    /**
     * @return the parsed search, or {@code null} when {@code raw} is null or
     *         blank — callers treat that as "no filter", i.e. the full feed
     * @throws IllegalArgumentException if {@code raw} exceeds {@link #MAX_LENGTH}
     */
    public static EpisodeSearch parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("search must be at most " + MAX_LENGTH + " characters");
        }
        Matcher m = EPISODE_NUMBER.matcher(trimmed);
        Integer number = m.matches() ? Integer.valueOf(m.group(1)) : null;
        return new EpisodeSearch(trimmed.toLowerCase(Locale.ROOT), number);
    }
}
