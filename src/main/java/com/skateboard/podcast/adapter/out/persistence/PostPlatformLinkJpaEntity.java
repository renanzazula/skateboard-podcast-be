package com.skateboard.podcast.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_platform_link")
public class PostPlatformLinkJpaEntity {

    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "external_url", nullable = false, columnDefinition = "text")
    private String externalUrl;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public PostPlatformLinkJpaEntity() {}

    public UUID getId()             { return id; }
    public UUID getPostId()         { return postId; }
    public String getPlatform()     { return platform; }
    public String getExternalId()   { return externalId; }
    public String getExternalUrl()  { return externalUrl; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }

    public void setId(UUID v)            { this.id = v; }
    public void setPostId(UUID v)        { this.postId = v; }
    public void setPlatform(String v)    { this.platform = v; }
    public void setExternalId(String v)  { this.externalId = v; }
    public void setExternalUrl(String v) { this.externalUrl = v; }
    public void setCreatedAt(Instant v)  { this.createdAt = v; }
    public void setUpdatedAt(Instant v)  { this.updatedAt = v; }
}
