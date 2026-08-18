package com.skateboard.podcast.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a raw YouTube video description into the clean episode description
 * and the guest's social media links, per .docs/README_YOUTUBE_DESCRIPTION_FILTERING.md.
 * Support links, presenters, sponsors, coupons, addresses, editor credit and
 * hashtags are metadata and are discarded — only the text before the first
 * recognized marker (the description) and the CONVIDADO(S) section (guest
 * links) are kept.
 */
@Component
public class YoutubeDescriptionParser {

    public record ParsedDescription(String description, String socialMediaLinksJson) {}

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    // Matched against a single (trimmed) line, so ^ already anchors to that line's start.
    private static final Pattern METADATA_HEADER = Pattern.compile(
            "^(APOIE NOSSO CANAL|CONVIDADOS?|APRESENTADO POR|APOIO|EDI[CÇ][AÃ]O)\\s*:?", FLAGS);
    private static final Pattern GUEST_HEADER = Pattern.compile("^CONVIDADOS?\\s*:\\s*(.*)$", FLAGS);
    private static final Pattern SEPARATOR_LINE = Pattern.compile("^-{3,}$");
    private static final Pattern INSTAGRAM_URL = Pattern.compile(
            "https?://(?:www\\.)?instagram\\.com/([A-Za-z0-9._]+)/?(?:\\?\\S*)?", FLAGS);

    private final ObjectMapper objectMapper;

    public YoutubeDescriptionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedDescription parse(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return new ParsedDescription("", "[]");
        }
        String normalized = rawDescription.replace("\r\n", "\n").replace("\r", "\n").trim();
        String[] lines = normalized.split("\n");

        String description = extractDescription(lines);
        List<Map<String, String>> socialLinks = extractGuestSocialLinks(lines);
        return new ParsedDescription(description, toJson(socialLinks));
    }

    private String extractDescription(String[] lines) {
        StringBuilder clean = new StringBuilder();
        for (String line : lines) {
            if (METADATA_HEADER.matcher(line.trim()).find()) {
                break;
            }
            if (clean.length() > 0) clean.append('\n');
            clean.append(line);
        }
        return clean.toString().trim();
    }

    private List<Map<String, String>> extractGuestSocialLinks(String[] lines) {
        StringBuilder guestSection = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (guestSection == null) {
                Matcher header = GUEST_HEADER.matcher(trimmed);
                if (header.matches()) {
                    guestSection = new StringBuilder(header.group(1));
                }
                continue;
            }
            if (SEPARATOR_LINE.matcher(trimmed).matches() || isOtherMetadataHeader(trimmed)) {
                break;
            }
            guestSection.append('\n').append(line);
        }
        if (guestSection == null) return List.of();

        List<Map<String, String>> links = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        Matcher matcher = INSTAGRAM_URL.matcher(guestSection);
        while (matcher.find()) {
            String url = "https://www.instagram.com/" + matcher.group(1);
            if (seen.putIfAbsent(url, true) == null) {
                Map<String, String> link = new LinkedHashMap<>();
                link.put("platform", "instagram");
                link.put("url", url);
                links.add(link);
            }
        }
        return links;
    }

    /** Another recognized marker ends the guest section too, even without a "----" separator in between. */
    private boolean isOtherMetadataHeader(String trimmedLine) {
        return METADATA_HEADER.matcher(trimmedLine).find() && !GUEST_HEADER.matcher(trimmedLine).matches();
    }

    private String toJson(List<Map<String, String>> links) {
        if (links.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(links);
        } catch (Exception e) {
            return "[]";
        }
    }
}
