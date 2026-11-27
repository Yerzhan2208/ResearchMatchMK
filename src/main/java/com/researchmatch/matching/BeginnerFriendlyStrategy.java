package com.researchmatch.matching;

import com.researchmatch.models.MatchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Favours beginner-friendly opportunities; the engine pre-filters to beginner-friendly candidates before
 * ranking. Within the filtered set it ranks by {@code totalScore} descending, then ascending
 * {@code opportunityId}. Returns a new list and never mutates the input.
 */
@Component("beginnerFriendlyStrategy")
public class BeginnerFriendlyStrategy implements RankingStrategy {

    public static final String NAME = "beginner-friendly";

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
