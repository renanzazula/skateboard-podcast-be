package com.skateboard.podcast.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guest_applications")
public class GuestApplicationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "social_links_json", nullable = false, columnDefinition = "text")
    private String socialLinksJson;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    public GuestApplicationJpaEntity() {
        // required by JPA
    }

    public UUID getId()               { return id; }
    public UUID getUserId()           { return userId; }
    public String getName()           { return name; }
    public String getEmail()          { return email; }
    public String getMessage()        { return message; }
    public String getSocialLinksJson(){ return socialLinksJson; }
    public String getStatus()         { return status; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getUpdatedAt()     { return updatedAt; }
    public UUID getReviewedBy()       { return reviewedBy; }
    public Instant getNotifiedAt()    { return notifiedAt; }

    public void setId(UUID v)               { this.id = v; }
    public void setUserId(UUID v)           { this.userId = v; }
    public void setName(String v)           { this.name = v; }
    public void setEmail(String v)          { this.email = v; }
    public void setMessage(String v)        { this.message = v; }
    public void setSocialLinksJson(String v){ this.socialLinksJson = v; }
    public void setStatus(String v)         { this.status = v; }
    public void setCreatedAt(Instant v)     { this.createdAt = v; }
    public void setUpdatedAt(Instant v)     { this.updatedAt = v; }
    public void setReviewedBy(UUID v)       { this.reviewedBy = v; }
    public void setNotifiedAt(Instant v)    { this.notifiedAt = v; }
}
