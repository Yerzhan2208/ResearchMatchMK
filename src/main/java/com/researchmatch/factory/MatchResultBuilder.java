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
     * Returns the fully initialised result; never null.
     *
     * <p>Per {@code docs/integration-contracts.md} §5 this method performs <strong>no validation</strong>.
     * Unlike {@link OpportunityBuilder#build()}, which rejects bad input with {@code InvalidRequestException},
     * a {@code MatchResult} is a computed value rather than user input: its score, eligibility and skill sets
     * are produced by the matching chain (Member 2) and must be recorded as produced, so they can be inspected
     * and explained. Do not add invariants here — that would turn a faithful result record into a second,
     * competing source of truth about what a valid match is.
     */
    public MatchResult build() {
        return result;
    }
}
