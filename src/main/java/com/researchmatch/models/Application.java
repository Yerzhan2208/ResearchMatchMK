package com.researchmatch.models;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** A student's application to an opportunity. */
@Entity
@Table(name = "applications")
public class Application {

    /** Human-readable application lifecycle. State pattern logic lives in the {@code pipeline} package. */
    public enum Status {
        NEW,
        SHORTLISTED,
        INTERVIEW,
        ACCEPTED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long opportunityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NEW;

    @Column(length = 4000)
    private String statement;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "application_evidence_links", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "evidence_link")
    private Set<String> evidenceLinks = new LinkedHashSet<>();

    @Column(length = 4000)
    private String notes;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Application() {
    }

    public Application(Long id, Long studentId, Long opportunityId) {
        this.id = id;
        this.studentId = studentId;
        this.opportunityId = opportunityId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getOpportunityId() {
        return opportunityId;
    }

    public void setOpportunityId(Long opportunityId) {
        this.opportunityId = opportunityId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public Set<String> getEvidenceLinks() {
        return evidenceLinks;
    }

    public void setEvidenceLinks(Set<String> evidenceLinks) {
        this.evidenceLinks = evidenceLinks;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // Unsaved (id == null) entities are never equal to another instance.
        if (id == null || !(o instanceof Application other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
