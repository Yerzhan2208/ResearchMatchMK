package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExplanationHandlerTest {

    private final ExplanationHandler handler = new ExplanationHandler();

    private static MatchContext context(boolean eligible, Set<String> matched, Set<String> missing) {
        MatchContext context = new MatchContext(new StudentProfile(), new Opportunity());
        if (!eligible) {
            context.markIneligible("Requires academic year 3 or above");
        }
        context.addMatchedSkills(matched);
        context.addMissingSkills(missing);
        return context;
    }

    private MatchContext run(MatchContext context) {
        MatchLayerHandler.LayerResult result = handler.evaluate(context);
        assertThat(result).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
        return context;
    }

    // ---------------------------------------------------------------- eligible

    @Test
    void eligibleWithBothSkillsProducesSummaryAndPrep() {
        MatchContext context = run(context(true, Set.of("java", "sql"), Set.of("python")));

        assertThat(context.getExplanationSummary())
                .contains("eligible")
                .contains("JAVA")
                .contains("SQL")
                .containsIgnoringCase("still missing")
                .contains("PYTHON");
        assertThat(context.getRecommendedPrep()).containsIgnoringCase("PYTHON");
    }

    @Test
    void eligibleWithOnlyMissingSkillsRecommendsPreparation() {
        MatchContext context = run(context(true, Set.of(), Set.of("kotlin")));

        assertThat(context.getExplanationSummary()).containsIgnoringCase("still missing").contains("KOTLIN");
        assertThat(context.getRecommendedPrep()).containsIgnoringCase("strengthening").contains("KOTLIN");
    }

    @Test
    void eligibleWithOnlyMatchedSkillsSuggestsDiscussingWork() {
        MatchContext context = run(context(true, Set.of("java"), Set.of()));

        assertThat(context.getExplanationSummary()).contains("JAVA");
        assertThat(context.getRecommendedPrep()).containsIgnoringCase("discuss").contains("JAVA");
    }

    @Test
    void eligibleWithNoSkillsProducesNonBlankSummaryAndPrep() {
        MatchContext context = run(context(true, Set.of(), Set.of()));

        assertThat(context.getExplanationSummary()).isNotBlank();
        assertThat(context.getRecommendedPrep()).isNotBlank();
    }

    // ---------------------------------------------------------------- ineligible

    @Test
    void ineligibleProducesSummaryThatPreservesReasonAndAdvisesReview() {
        MatchContext context = run(context(false, Set.of(), Set.of()));

        assertThat(context.isEligible()).isFalse();
        assertThat(context.getExplanationSummary())
                .contains("not a match")
                .contains("Requires academic year 3");
        assertThat(context.getRecommendedPrep()).containsIgnoringCase("eligibility requirements");
    }

    @Test
    void ineligibleIgnoresSkillSuggestions() {
        MatchContext context = run(context(false, Set.of("java"), Set.of("python")));

        assertThat(context.getRecommendedPrep()).doesNotContainIgnoringCase("strengthening");
        assertThat(context.getExplanationSummary()).contains("not a match");
    }

    // ---------------------------------------------------------------- invariants

    @Test
    void doesNotChangeEligibilityOrScores() {
        MatchContext context = new MatchContext(new StudentProfile(), new Opportunity());
        context.addScore(SemanticMatchHandler.LAYER, 0.42);
        double beforeTotal = context.getTotalScore();

        run(context);

        assertThat(context.isEligible()).isTrue();
        assertThat(context.getTotalScore()).isEqualTo(beforeTotal);
        assertThat(context.getLayerScores()).doesNotContainKey(ExplanationHandler.LAYER);
    }

    @Test
    void summaryIsNeverBlankEvenForEmptyContext() {
        MatchContext context = run(new MatchContext(new StudentProfile(), new Opportunity()));

        assertThat(context.getExplanationSummary()).isNotBlank();
    }

    @Test
    void appendsToExistingNotesFromEarlierLayers() {
        MatchContext context = new MatchContext(new StudentProfile(), new Opportunity());
        context.addExplanationNote("semantic note");

        run(context);

        assertThat(context.getExplanationSummary()).startsWith("semantic note").isNotBlank();
    }

    @Test
    void nullAndBlankSkillEntriesAreIgnored() {
        Set<String> matched = new LinkedHashSet<>();
        matched.add(null);
        matched.add("  ");
        matched.add("java");
        MatchContext context = run(context(true, matched, null));

        assertThat(context.getExplanationSummary()).contains("JAVA");
        assertThat(context.getExplanationSummary()).doesNotContain("  ");
    }
}
