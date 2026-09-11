package com.skateboard.podcast.application.service;

import java.util.regex.Pattern;

/**
 * Recognises the show's canonical episode-title convention:
 * {@code Skateboard Podcast #<episode number>}, optionally preceded by other
 * text (the guest name, in practice).
 *
 * <p>Used to gate push notifications: the YouTube channel also carries
 * interviews, highlight reels and one-off specials, and only a real numbered
 * episode is worth waking a subscriber's phone for. A non-matching video is
 * still synced and stored like any other — see {@link PodcastPublicationNotifier}.
 *
 * <p>Matches: {@code TOM YUKIO - Skateboard Podcast #124}.
 * Does not match: {@code Skateboard Podcast Special}, {@code Skateboard Podcast},
 * {@code Skateboard Podcast Episode 124}.
 */
public final class PodcastTitlePattern {

    // Whitespace between "#" and the number is tolerated for the same reason
    // EpisodeNumberParser tolerates it ("... # 77").
    private static final Pattern NUMBERED_EPISODE =
            Pattern.compile("Skateboard\\s+Podcast\\s*#\\s*\\d+", Pattern.CASE_INSENSITIVE);

    private PodcastTitlePattern() {}

    public static boolean isNumberedEpisode(String title) {
        return title != null && NUMBERED_EPISODE.matcher(title).find();
    }
}
