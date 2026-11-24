package com.researchmatch.matching;

import com.researchmatch.models.MatchResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default strategy: rank by {@code totalScore} alone.
 *
 * <p>TODO (Member 2): implement {@link #rank(List)} (and any re-weighting) with clear tie-breaking.
 */
@Component("balancedMatchStrategy")
public class BalancedMatchStrategy implements RankingStrategy {

    public static final String NAME = "balanced";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<MatchResult> rank(List<MatchResult> results) {
        throw new UnsupportedOperationException("TODO (Member 2): BalancedMatchStrategy.rank");
    }
}
