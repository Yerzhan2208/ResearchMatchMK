package com.researchmatch.matching;

import com.researchmatch.models.MatchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Default strategy: rank by {@code totalScore} descending, then {@code opportunityId} ascending as a stable
 * tie-breaker. Returns a new list and never mutates the input.
 */
@Component("balancedMatchStrategy")
public class BalancedMatchStrategy implements RankingStrategy {

    public static final String NAME = "balanced";

    static final Comparator<MatchResult> ORDER =
            Comparator.comparingDouble(MatchResult::getTotalScore).reversed()
                    .thenComparing(MatchResult::getOpportunityId,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<MatchResult> rank(List<MatchResult> results) {
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }
        List<MatchResult> ranked = new ArrayList<>(results);
        ranked.sort(ORDER);
        return ranked;
    }
}
