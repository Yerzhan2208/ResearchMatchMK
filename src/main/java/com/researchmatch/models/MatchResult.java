package com.researchmatch.models;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The outcome of scoring a student against an opportunity.
 *
 * <p>Identified by the (studentId, opportunityId) pair; built with {@code MatchResultBuilder}.
 */
@Entity
@Table(name = "match_results")
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long opportunityId;

    private double totalScore;

    @Column(nullable = false)
    private boolean eligible;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "match_result_matched_skills", joinColumns = @JoinColumn(name = "match_result_id"))
    @Column(name = "skill")
    private Set<String> matchedSkills = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "match_result_missing_skills", joinColumns = @JoinColumn(name = "match_result_id"))
    @Column(name = "skill")
    private Set<String> missingSkills = new LinkedHashSet<>();

    @Column(length = 2000)
    private String explanationSummary;

    @Column(length = 2000)
    private String recommendedPrep;

    public MatchResult() {
    }

    public MatchResult(Long id, Long studentId, Long opportunityId) {
        this.id = id;
        this.studentId = studentId;
        this.opportunityId = opportunityId;
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

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public Set<String> getMatchedSkills() {
        return matchedSkills;
    }

    public void setMatchedSkills(Set<String> matchedSkills) {
        this.matchedSkills = matchedSkills;
    }

    public Set<String> getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(Set<String> missingSkills) {
        this.missingSkills = missingSkills;
    }

    public String getExplanationSummary() {
        return explanationSummary;
    }

    public void setExplanationSummary(String explanationSummary) {
        this.explanationSummary = explanationSummary;
    }

    public String getRecommendedPrep() {
        return recommendedPrep;
    }

    public void setRecommendedPrep(String recommendedPrep) {
        this.recommendedPrep = recommendedPrep;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // Unsaved (id == null) entities are never equal to another instance.
        if (id == null || !(o instanceof MatchResult other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
