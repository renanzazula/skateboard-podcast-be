package com.skateboard.podcast.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Category {

    private final UUID id;
    private String slug;
    private String name;
    private String description;
    private String coverUrl;
    private final String source;
    private final String externalId;
    private boolean enabled;
    private Integer displayOrder;
    private boolean isDefault;
    private final Instant createdAt;
    private Instant updatedAt;

    private Category(UUID id, String slug, String name, String description, String coverUrl,
                     String source, String externalId, boolean enabled, Integer displayOrder,
                     boolean isDefault, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.coverUrl = coverUrl;
        this.source = source;
        this.externalId = externalId;
        this.enabled = enabled;
        this.displayOrder = displayOrder;
        this.isDefault = isDefault;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Category createFromYoutube(String slug, String externalId, String name,
                                              String description, String coverUrl, boolean isDefault) {
        Instant now = Instant.now();
        return new Category(UUID.randomUUID(), slug, name, description, coverUrl,
                "YOUTUBE", externalId, true, null, isDefault, now, now);
    }

    public static Category reconstitute(UUID id, String slug, String name, String description,
                                        String coverUrl, String source, String externalId,
                                        boolean enabled, Integer displayOrder, boolean isDefault,
                                        Instant createdAt, Instant updatedAt) {
        return new Category(id, slug, name, description, coverUrl, source, externalId,
                enabled, displayOrder, isDefault, createdAt, updatedAt);
    }

    /** Sync-only: refreshes name/description/cover/default flag from the source playlist. */
    public void updateFromYoutube(String name, String description, String coverUrl, boolean isDefault) {
        this.name = name;
        this.description = description;
        this.coverUrl = coverUrl;
        this.isDefault = isDefault;
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    /** Sync-only: the source playlist no longer exists on YouTube. */
    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    public UUID getId()             { return id; }
    public String getSlug()         { return slug; }
    public String getName()         { return name; }
    public String getDescription()  { return description; }
    public String getCoverUrl()     { return coverUrl; }
    public String getSource()       { return source; }
    public String getExternalId()   { return externalId; }
    public boolean isEnabled()      { return enabled; }
    public Integer getDisplayOrder(){ return displayOrder; }
    public boolean isDefault()      { return isDefault; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }
}
