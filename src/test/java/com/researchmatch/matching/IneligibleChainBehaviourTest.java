package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents the chain behaviour for an ineligible student: {@link EligibilityFilterHandler} returns
 * {@code STOP}, and the default {@link MatchLayerHandler#handle} short-circuits, so neither
 * {@link EvidenceScoreHandler} nor {@link ExplanationHandler} runs. Consequently the summary is the
 * rejection reason recorded by eligibility, and {@link MatchContext#getRecommendedPrep()} stays null
 * because the only handler that sets it ({@code ExplanationHandler}) is skipped.
 */
class IneligibleChainBehaviourTest {

    @Test
    void eligibilityStopSkipsExplanationAndLeavesPrepNull() {
        StudentProfile student = new StudentProfile();
        student.setEmail("student@example.com");
        student.setHoursPerWeek(10);
        student.setAcademicYear(1);

        Opportunity opportunity = new Opportunity();
        opportunity.setBeginnerFriendly(true);
        opportunity.setMinAcademicYear(3);

        MatchContext context = new MatchContext(student, opportunity);

        MatchLayerHandler chain = new EligibilityFilterHandler();
        MatchLayerHandler second = new SemanticMatchHandler();
        MatchLayerHandler third = new EvidenceScoreHandler();
        MatchLayerHandler tail = new ExplanationHandler();

        MatchContext result = chain.handle(context, wrap(second, wrap(third, tail)));

        assertThat(result).isSameAs(context);
        assertThat(context.isEligible()).isFalse();
        assertThat(context.getExplanationSummary()).isNotBlank().contains("Requires academic year 3");
        assertThat(context.getRecommendedPrep()).isNull();
        assertThat(context.getLayerScores())
                .doesNotContainKey(SemanticMatchHandler.LAYER)
                .doesNotContainKey(EvidenceScoreHandler.LAYER)
                .doesNotContainKey(ExplanationHandler.LAYER);
    }

    /** Wraps a handler so it defers to {@code next}, mimicking how {@code MatchingEngine} assembles the chain. */
    private static MatchLayerHandler wrap(MatchLayerHandler handler, MatchLayerHandler next) {
        return new MatchLayerHandler() {
            @Override
            public String layerName() {
                return handler.layerName();
            }

            @Override
            public LayerResult evaluate(MatchContext ctx) {
                return handler.evaluate(ctx);
            }

            @Override
            public MatchContext handle(MatchContext ctx, MatchLayerHandler ignored) {
                return MatchLayerHandler.super.handle(ctx, next);
            }
        };
    }
}
