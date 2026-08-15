package com.skateboard.podcast.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category")
public class CategoryJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String slug;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "cover_url", columnDefinition = "text")
    private String coverUrl;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CategoryJpaEntity() {}

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

    public void setId(UUID v)             { this.id = v; }
    public void setSlug(String v)         { this.slug = v; }
    public void setName(String v)         { this.name = v; }
    public void setDescription(String v)  { this.description = v; }
    public void setCoverUrl(String v)     { this.coverUrl = v; }
    public void setSource(String v)       { this.source = v; }
    public void setExternalId(String v)   { this.externalId = v; }
    public void setEnabled(boolean v)     { this.enabled = v; }
    public void setDisplayOrder(Integer v){ this.displayOrder = v; }
    public void setDefault(boolean v)     { this.isDefault = v; }
    public void setCreatedAt(Instant v)   { this.createdAt = v; }
    public void setUpdatedAt(Instant v)   { this.updatedAt = v; }
}
