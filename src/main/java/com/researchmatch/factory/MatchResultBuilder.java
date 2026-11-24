package com.researchmatch.factory;

import com.researchmatch.models.MatchResult;

import java.util.Collection;

/**
 * Builder for {@link MatchResult} (Member 3).
 *
 * <p>Consumers: {@code MatchingEngine} (Member 2), {@code ResearchMatchFacade} (Member 4).
 * {@link #build()} returns a fully initialised entity with non-null collections; it never returns null
 * and never validates domain rules (the matching chain owns those).
 */
public class MatchResultBuilder {

    private final MatchResult result = new MatchResult();

    public MatchResultBuilder studentId(Long studentId) {
        result.setStudentId(studentId);
        return this;
    }

    public MatchResultBuilder opportunityId(Long opportunityId) {
        result.setOpportunityId(opportunityId);
        return this;
    }

    public MatchResultBuilder totalScore(double totalScore) {
        result.setTotalScore(totalScore);
        return this;
    }

    public MatchResultBuilder eligible(boolean eligible) {
        result.setEligible(eligible);
        return this;
    }

    public MatchResultBuilder matchedSkills(Collection<String> skills) {
        result.setMatchedSkills(skills == null ? new java.util.LinkedHashSet<>() : new java.util.LinkedHashSet<>(skills));
        return this;
    }

    public MatchResultBuilder missingSkills(Collection<String> skills) {
        result.setMissingSkills(skills == null ? new java.util.LinkedHashSet<>() : new java.util.LinkedHashSet<>(skills));
        return this;
    }

    public MatchResultBuilder explanationSummary(String explanationSummary) {
        result.setExplanationSummary(explanationSummary);
        return this;
    }

    public MatchResultBuilder recommendedPrep(String recommendedPrep) {
        result.setRecommendedPrep(recommendedPrep);
        return this;
    }

    /**
     * Returns the fully initialised result.
     *
     * <p>Intentionally free of domain invariants: score bounds and eligibility are the matching chain's
     * (Member 2) rules, and duplicating them here would give the same rule two owners.
     */
    public MatchResult build() {
        return result;
    }
}
