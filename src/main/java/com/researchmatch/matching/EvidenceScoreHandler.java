package com.researchmatch.matching;

import org.springframework.stereotype.Component;

/**
 * Adds the bonus for skills backed by verified evidence (see {@code SkillEvidence}).
 *
 * <p>TODO (Member 2): implement {@link #apply(MatchContext)}; read
 * {@link MatchContext#verifiedSkillSet()} and add the score under {@link #LAYER}.
 */
@Component
public class EvidenceScoreHandler extends AbstractMatchLayerHandler {

    public static final String LAYER = "evidence";

    @Override
    public String layerName() {
        return LAYER;
    }

    @Override
    protected MatchLayerHandler.LayerResult apply(MatchContext context) {
        throw new UnsupportedOperationException("TODO (Member 2): EvidenceScoreHandler.apply");
    }
}
