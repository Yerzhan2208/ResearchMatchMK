package com.researchmatch.matching;

import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.SystemConfigManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the small remaining lines/branches of {@link MatchContext}, {@link SkillStrictStrategy} and
 * {@link SemanticMatchHandler} that the end-to-end scenarios do not naturally exercise (mutable setters,
 * null-collection guards, and the null-config constructor fallback).
 */
class MatchingPlumbingCoverageTest {

    @Test
    void matchContext_settersAndTotals() {
        StudentProfile student = new StudentProfile();
        student.setEmail("s@x.edu");
        student.setVerifiedSkills(null);
        Opportunity opportunity = new Opportunity();
        opportunity.setFacultyId(1L);
        MatchContext context = new MatchContext(student, opportunity);

        context.setTotalScore(3.5);
        assertThat(context.getTotalScore()).isEqualTo(3.5);

        context.addScore("layer", 1.5);
        assertThat(context.getLayerScores()).containsEntry("layer", 1.5);
        assertThat(context.getTotalScore()).isEqualTo(5.0);

        // null verifiedSkills yields an empty set, not an NPE.
        assertThat(context.verifiedSkillSet()).isEmpty();

        context.setEligible(false);
        assertThat(context.isEligible()).isFalse();
    }

    @Test
    void skillStrict_handlesNullMissingSkills() {
        MatchResult withoutMissing = new MatchResult();
        withoutMissing.setOpportunityId(1L);
        withoutMissing.setTotalScore(1.0);
        withoutMissing.setMissingSkills(null);

        MatchResult withMissing = new MatchResult();
        withMissing.setOpportunityId(2L);
        withMissing.setTotalScore(1.0);
        withMissing.setMissingSkills(java.util.Set.of("Java"));

        List<MatchResult> ranked = new SkillStrictStrategy().rank(List.of(withMissing, withoutMissing));
        // null missing-skills counts as zero gaps, so it ranks first.
        assertThat(ranked.get(0)).isSameAs(withoutMissing);
    }

    @Test
    void semanticHandler_nullConfigFallsBackToSingleton() {
        SemanticMatchHandler handler = new SemanticMatchHandler((SystemConfigManager) null);
        StudentProfile student = new StudentProfile();
        student.setEmail("s@x.edu");
        student.setClaimedSkills(java.util.Set.of("Java"));
        Opportunity opportunity = new Opportunity();
        opportunity.setFacultyId(1L);
        opportunity.setTitle("Java project");

        MatchContext context = new MatchContext(student, opportunity);
        assertThat(handler.evaluate(context)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
    }
}
