package com.researchmatch.matching;

import org.springframework.stereotype.Component;

/**
 * First link: hard eligibility rules (academic year, hours available, applicant cap, ...).
 *
 * <p>MUST reject when {@code opportunity.getMinAcademicYear() != null &&
 * (student.getAcademicYear() == null || student.getAcademicYear() < opportunity.getMinAcademicYear())}.
 *
 * <p>TODO (Member 2): implement {@link #apply(MatchContext)}. On failure call
 * {@link MatchContext#markIneligible(String)} and return {@link LayerResult#STOP}.
 */
@Component
public class EligibilityFilterHandler extends AbstractMatchLayerHandler {

    public static final String LAYER = "eligibility";

    @Override
    public String layerName() {
        return LAYER;
    }

    @Override
    protected MatchLayerHandler.LayerResult apply(MatchContext context) {
        throw new UnsupportedOperationException("TODO (Member 2): EligibilityFilterHandler.apply");
    }
}
