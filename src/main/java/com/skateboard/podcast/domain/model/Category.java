package com.skateboard.podcast.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Category {

    private final UUID id;
    private String slug;
    private String name;
    private String customName;
    private String description;
    private String coverUrl;
    private final String source;
    private final String externalId;
    private boolean enabled;
    private Integer displayOrder;
    private boolean isDefault;
    private boolean defaultLocked;
    private final Instant createdAt;
    private Instant updatedAt;

    private Category(UUID id, String slug, String name, String customName, String description,
                     String coverUrl, String source, String externalId, boolean enabled,
                     Integer displayOrder, boolean isDefault, boolean defaultLocked,
                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.customName = customName;
        this.description = description;
        this.coverUrl = coverUrl;
        this.source = source;
        this.externalId = externalId;
        this.enabled = enabled;
        this.displayOrder = displayOrder;
        this.isDefault = isDefault;
        this.defaultLocked = defaultLocked;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Category createFromYoutube(String slug, String externalId, String name,
                                              String description, String coverUrl, boolean isDefault) {
        Instant now = Instant.now();
        return new Category(UUID.randomUUID(), slug, name, null, description, coverUrl,
                "YOUTUBE", externalId, true, null, isDefault, false, now, now);
    }

    public static Category reconstitute(UUID id, String slug, String name, String customName,
                                        String description, String coverUrl, String source,
                                        String externalId, boolean enabled, Integer displayOrder,
                                        boolean isDefault, boolean defaultLocked,
                                        Instant createdAt, Instant updatedAt) {
        return new Category(id, slug, name, customName, description, coverUrl, source, externalId,
                enabled, displayOrder, isDefault, defaultLocked, createdAt, updatedAt);
    }

    /**
     * Sync-only: refreshes name/description/cover from the source playlist.
     * The default flag is config-driven bootstrap behavior and stops applying
     * once an admin has picked a default ({@code defaultLocked}); the name
     * refresh never conflicts with an admin rename, which lives in
     * {@code customName}.
     */
    public void updateFromYoutube(String name, String description, String coverUrl, boolean isDefault) {
        this.name = name;
        this.description = description;
        this.coverUrl = coverUrl;
        if (!defaultLocked) {
            this.isDefault = isDefault;
        }
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    // ── Admin mutations (survive the sync by design) ────────────────────────

    /** Admin rename: overrides the display name; {@code null}/blank resets to the YouTube title. */
    public void rename(String customName) {
        this.customName = customName == null || customName.isBlank() ? null : customName.trim();
        this.updatedAt = Instant.now();
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
        this.updatedAt = Instant.now();
    }

    /** Makes this category the default and freezes defaulting against the sync. */
    public void markDefault() {
        this.isDefault = true;
        this.defaultLocked = true;
        this.updatedAt = Instant.now();
    }

    /** Clears the default flag while keeping defaulting admin-owned. */
    public void clearDefault() {
        this.isDefault = false;
        this.defaultLocked = true;
        this.updatedAt = Instant.now();
    }

    /** Sync-only: the source playlist no longer exists on YouTube. */
    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    /** The name the app shows: the admin override when set, else the YouTube title. */
    public String getEffectiveName() {
        return customName != null ? customName : name;
    }

    public UUID getId()             { return id; }
    public String getSlug()         { return slug; }
    public String getName()         { return name; }
    public String getCustomName()   { return customName; }
    public String getDescription()  { return description; }
    public String getCoverUrl()     { return coverUrl; }
    public String getSource()       { return source; }
    public String getExternalId()   { return externalId; }
    public boolean isEnabled()      { return enabled; }
    public Integer getDisplayOrder(){ return displayOrder; }
    public boolean isDefault()      { return isDefault; }
    public boolean isDefaultLocked(){ return defaultLocked; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }
}
