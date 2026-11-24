package com.researchmatch.matching;

import com.researchmatch.models.MatchResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Favours beginner-friendly opportunities and lower hours-per-week commitments.
 *
 * <p>TODO (Member 2): implement {@link #rank(List)}. Needs the {@code Opportunity} behind each result and a
 * {@code MatchResult -> Opportunity} lookup; extend the constructor with the repository you need.
 */
@Component("beginnerFriendlyStrategy")
public class BeginnerFriendlyStrategy implements RankingStrategy {

    public static final String NAME = "beginner-friendly";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<MatchResult> rank(List<MatchResult> results) {
        throw new UnsupportedOperationException("TODO (Member 2): BeginnerFriendlyStrategy.rank");
    }
}
