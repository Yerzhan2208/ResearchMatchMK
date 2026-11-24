package com.researchmatch.matching;

import org.springframework.stereotype.Component;

/**
 * Scores interest / description similarity and records matched &amp; missing skills.
 *
 * <p>Weight comes from {@code SystemConfigManager.KEY_SEMANTIC_WEIGHT}, managed by Member 1.
 *
 * <p>TODO (Member 2): implement {@link #apply(MatchContext)} and record the score with
 * {@link MatchContext#addScore(String, double)} (key: {@link #LAYER}).
 */
@Component
public class SemanticMatchHandler extends AbstractMatchLayerHandler {

    public static final String LAYER = "semantic";

    @Override
    public String layerName() {
        return LAYER;
    }

    @Override
    protected MatchLayerHandler.LayerResult apply(MatchContext context) {
        throw new UnsupportedOperationException("TODO (Member 2): SemanticMatchHandler.apply");
    }
}
