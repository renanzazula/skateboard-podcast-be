package com.skateboard.podcast.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class PostJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 500)
    private String slug;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(nullable = false, length = 20)
    private String status;

    @Column
    private Instant publishAt;

    @Column(columnDefinition = "text")
    private String coverUrl;

    @Column(nullable = false, columnDefinition = "text")
    private String blocksJson;

    @Column(nullable = false, columnDefinition = "text")
    private String socialMediaLinksJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private UUID createdBy;

    public PostJpaEntity() {}

    public UUID getId()          { return id; }
    public String getSlug()      { return slug; }
    public String getTitle()     { return title; }
    public String getStatus()    { return status; }
    public Instant getPublishAt(){ return publishAt; }
    public String getCoverUrl()  { return coverUrl; }
    public String getBlocksJson()           { return blocksJson; }
    public String getSocialMediaLinksJson() { return socialMediaLinksJson; }
    public Instant getCreatedAt()           { return createdAt; }
    public Instant getUpdatedAt(){ return updatedAt; }
    public UUID getCreatedBy()   { return createdBy; }

    public void setId(UUID id)              { this.id = id; }
    public void setSlug(String slug)        { this.slug = slug; }
    public void setTitle(String title)      { this.title = title; }
    public void setStatus(String status)    { this.status = status; }
    public void setPublishAt(Instant v)     { this.publishAt = v; }
    public void setCoverUrl(String v)       { this.coverUrl = v; }
    public void setBlocksJson(String v)              { this.blocksJson = v; }
    public void setSocialMediaLinksJson(String v)    { this.socialMediaLinksJson = v; }
    public void setCreatedAt(Instant v)              { this.createdAt = v; }
    public void setUpdatedAt(Instant v)     { this.updatedAt = v; }
    public void setCreatedBy(UUID v)        { this.createdBy = v; }
}
