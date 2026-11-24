package com.researchmatch.matching;

import com.researchmatch.models.MatchResult;

import java.util.List;

/**
 * Strategy: decides how scored candidates are adjusted and ranked.
 *
 * <p>The engine produces results with a strategy-neutral {@code totalScore}; the strategy returns a new
 * ordered list (it must not mutate the input).
 */
public interface RankingStrategy {

    /** Stable identifier used by callers/UI, e.g. {@code "balanced"}. */
    String name();

    /**
     * @param results never null, may be empty; elements are never null
     * @return a new list, highest priority first; never null
     */
    List<MatchResult> rank(List<MatchResult> results);
}
