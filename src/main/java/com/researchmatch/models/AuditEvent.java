package com.researchmatch.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/** Append-only audit trail entry, written by {@code AuditLogObserver}. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id of the entity the action happened to (e.g. an application id). */
    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private String action;

    @Column(length = 4000)
    private String details;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public AuditEvent() {
    }

    public AuditEvent(Long id, Long entityId, String action, String details) {
        this.id = id;
        this.entityId = entityId;
        this.action = action;
        this.details = details;
    }

    @PrePersist
    void stamp() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // Unsaved (id == null) entities are never equal to another instance.
        if (id == null || !(o instanceof AuditEvent other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
