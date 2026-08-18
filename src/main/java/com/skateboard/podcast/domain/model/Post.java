package com.skateboard.podcast.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Post {

    private final UUID id;
    private String slug;
    private String title;
    private PostStatus status;
    private Instant publishAt;
    private String coverUrl;
    // Intrinsic pixel size of coverUrl; null unless the YouTube sync captured
    // it. Lets clients lay out a cover without measuring the image first.
    private Integer coverWidth;
    private Integer coverHeight;
    private String blocksJson;
    private String socialMediaLinksJson;
    private final Instant createdAt;
    private Instant updatedAt;
    private final UUID createdBy;
    private String youtubeVideoId;
    private String description;
    private Integer durationSeconds;
    private Integer episodeNumber;
    private final List<PostPlatformLink> platformLinks;

    private Post(UUID id, String slug, String title, PostStatus status, Instant publishAt,
                 String coverUrl, Integer coverWidth, Integer coverHeight,
                 String blocksJson, String socialMediaLinksJson,
                 Instant createdAt, Instant updatedAt, UUID createdBy,
                 String youtubeVideoId, String description, Integer durationSeconds, Integer episodeNumber,
                 List<PostPlatformLink> platformLinks) {
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.status = status;
        this.publishAt = publishAt;
        this.coverUrl = coverUrl;
        this.coverWidth = coverWidth;
        this.coverHeight = coverHeight;
        this.blocksJson = blocksJson;
        this.socialMediaLinksJson = socialMediaLinksJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.youtubeVideoId = youtubeVideoId;
        this.description = description;
        this.durationSeconds = durationSeconds;
        this.episodeNumber = episodeNumber;
        this.platformLinks = platformLinks != null ? new ArrayList<>(platformLinks) : new ArrayList<>();
    }

    public static Post create(String title, String slug, PostStatus status, Instant publishAt,
                              String coverUrl, String blocksJson, String socialMediaLinksJson, UUID createdBy) {
        Instant now = Instant.now();
        return new Post(UUID.randomUUID(), slug, title, status, publishAt, coverUrl, null, null, blocksJson,
                socialMediaLinksJson != null ? socialMediaLinksJson : "[]", now, now, createdBy,
                null, null, null, null, null);
    }

    public static Post reconstitute(UUID id, String slug, String title, PostStatus status, Instant publishAt,
                                    String coverUrl, Integer coverWidth, Integer coverHeight,
                                    String blocksJson, String socialMediaLinksJson,
                                    Instant createdAt, Instant updatedAt, UUID createdBy,
                                    String youtubeVideoId, String description, Integer durationSeconds, Integer episodeNumber,
                                    List<PostPlatformLink> platformLinks) {
        return new Post(id, slug, title, status, publishAt, coverUrl, coverWidth, coverHeight, blocksJson,
                socialMediaLinksJson != null ? socialMediaLinksJson : "[]", createdAt, updatedAt, createdBy,
                youtubeVideoId, description, durationSeconds, episodeNumber, platformLinks);
    }

    public void update(String title, String slug, PostStatus status, Instant publishAt,
                       String coverUrl, String blocksJson, String socialMediaLinksJson) {
        this.title = title;
        this.slug = slug;
        this.status = status;
        this.publishAt = publishAt;
        this.coverUrl = coverUrl;
        this.blocksJson = blocksJson;
        this.socialMediaLinksJson = socialMediaLinksJson != null ? socialMediaLinksJson : "[]";
        this.updatedAt = Instant.now();
    }

    /** Sync-only: attaches YouTube-sourced structured metadata at ingestion time. */
    public void attachYoutubeMetadata(String youtubeVideoId, String description, Integer durationSeconds, Integer episodeNumber) {
        this.youtubeVideoId = youtubeVideoId;
        this.description = description;
        this.durationSeconds = durationSeconds;
        this.episodeNumber = episodeNumber;
    }

    /** Sync-only: records the intrinsic pixel size of the cover the sync just set. */
    public void attachCoverDimensions(Integer coverWidth, Integer coverHeight) {
        this.coverWidth = coverWidth;
        this.coverHeight = coverHeight;
    }

    /** Sync-only: attaches/replaces this episode's link for {@code link.platform()} — at most one link per platform. */
    public void attachPlatformLink(PostPlatformLink link) {
        platformLinks.removeIf(existing -> existing.platform() == link.platform());
        platformLinks.add(link);
    }

    public UUID getId()                        { return id; }
    public String getSlug()                    { return slug; }
    public String getTitle()                   { return title; }
    public PostStatus getStatus()              { return status; }
    public Instant getPublishAt()              { return publishAt; }
    public String getCoverUrl()                { return coverUrl; }
    public Integer getCoverWidth()             { return coverWidth; }
    public Integer getCoverHeight()            { return coverHeight; }
    public String getBlocksJson()              { return blocksJson; }
    public String getSocialMediaLinksJson()    { return socialMediaLinksJson; }
    public Instant getCreatedAt()              { return createdAt; }
    public Instant getUpdatedAt()              { return updatedAt; }
    public UUID getCreatedBy()                 { return createdBy; }
    public String getYoutubeVideoId()          { return youtubeVideoId; }
    public String getDescription()             { return description; }
    public Integer getDurationSeconds()        { return durationSeconds; }
    public Integer getEpisodeNumber()          { return episodeNumber; }
    public List<PostPlatformLink> getPlatformLinks() { return Collections.unmodifiableList(platformLinks); }
}
