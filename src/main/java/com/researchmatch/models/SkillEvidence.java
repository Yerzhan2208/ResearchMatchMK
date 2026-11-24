package com.researchmatch.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/** Evidence that a student possesses a skill (e.g. a repository snippet verified by the student). */
@Entity
@Table(name = "skill_evidence")
public class SkillEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private String skillName;

    private String repoName;

    @Column(length = 2000)
    private String evidenceSnippet;

    @Column(nullable = false)
    private boolean verifiedByStudent;

    public SkillEvidence() {
    }

    public SkillEvidence(Long id, Long studentId, String skillName, String repoName, String evidenceSnippet) {
        this.id = id;
        this.studentId = studentId;
        this.skillName = skillName;
        this.repoName = repoName;
        this.evidenceSnippet = evidenceSnippet;
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

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getEvidenceSnippet() {
        return evidenceSnippet;
    }

    public void setEvidenceSnippet(String evidenceSnippet) {
        this.evidenceSnippet = evidenceSnippet;
    }

    public boolean isVerifiedByStudent() {
        return verifiedByStudent;
    }

    public void setVerifiedByStudent(boolean verifiedByStudent) {
        this.verifiedByStudent = verifiedByStudent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // Unsaved (id == null) entities are never equal to another instance.
        if (id == null || !(o instanceof SkillEvidence other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
