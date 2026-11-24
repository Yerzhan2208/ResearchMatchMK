package com.researchmatch.matching;

import org.springframework.stereotype.Component;

/**
 * Final link: turns the notes gathered so far into a human-readable summary and preparation advice.
 *
 * <p>TODO (Member 2): implement {@link #apply(MatchContext)}; write
 * {@link MatchContext#setRecommendedPrep(String)} and ensure
 * {@link MatchContext#getExplanationSummary()} is non-blank for an evaluated pair.
 */
@Component
public class ExplanationHandler extends AbstractMatchLayerHandler {

    public static final String LAYER = "explanation";

    @Override
    public String layerName() {
        return LAYER;
    }

    @Override
    protected MatchLayerHandler.LayerResult apply(MatchContext context) {
        throw new UnsupportedOperationException("TODO (Member 2): ExplanationHandler.apply");
    }
}
