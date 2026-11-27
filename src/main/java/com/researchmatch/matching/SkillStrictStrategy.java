package com.researchmatch.matching;

import com.researchmatch.models.MatchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Penalises candidates with missing required skills: fewer gaps rank first, then higher {@code totalScore},
 * then ascending {@code opportunityId}. Returns a new list and never mutates the input.
 */
@Component("skillStrictStrategy")
public class SkillStrictStrategy implements RankingStrategy {

    public static final String NAME = "skill-strict";

    static final Comparator<MatchResult> ORDER =
            Comparator.comparingInt(SkillStrictStrategy::missingCount)
                    .thenComparing(Comparator.comparingDouble(MatchResult::getTotalScore).reversed())
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

    private static int missingCount(MatchResult result) {
        return result.getMissingSkills() == null ? 0 : result.getMissingSkills().size();
    }
}
