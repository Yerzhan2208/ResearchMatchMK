package com.researchmatch.matching;

import com.researchmatch.models.MatchResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Penalises candidates with missing required skills; results with fewer gaps rank first.
 *
 * <p>TODO (Member 2): implement {@link #rank(List)}.
 */
@Component("skillStrictStrategy")
public class SkillStrictStrategy implements RankingStrategy {

    public static final String NAME = "skill-strict";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<MatchResult> rank(List<MatchResult> results) {
        throw new UnsupportedOperationException("TODO (Member 2): SkillStrictStrategy.rank");
    }
}
