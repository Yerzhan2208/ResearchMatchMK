package com.researchmatch.matching;

import com.researchmatch.models.MatchResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RankingStrategyTest {

    private static MatchResult result(long opportunityId, double totalScore, Set<String> missing) {
        MatchResult result = new MatchResult();
        result.setOpportunityId(opportunityId);
        result.setStudentId(1L);
        result.setTotalScore(totalScore);
        result.setMissingSkills(missing == null ? new java.util.LinkedHashSet<>() : new java.util.LinkedHashSet<>(missing));
        return result;
    }

    private static List<Long> ids(List<MatchResult> results) {
        return results.stream().map(MatchResult::getOpportunityId).toList();
    }

    @Nested
    class Balanced {

        private final RankingStrategy strategy = new BalancedMatchStrategy();

        @Test
        void ordersByScoreDescending() {
            List<MatchResult> ranked = strategy.rank(List.of(
                    result(10L, 1.0, Set.of()),
                    result(20L, 5.0, Set.of()),
                    result(30L, 3.0, Set.of())));

            assertThat(ids(ranked)).containsExactly(20L, 30L, 10L);
        }

        @Test
        void breaksScoreTiesByOpportunityIdAscending() {
            List<MatchResult> ranked = strategy.rank(List.of(
                    result(30L, 2.0, Set.of()),
                    result(10L, 2.0, Set.of()),
                    result(20L, 2.0, Set.of())));

            assertThat(ids(ranked)).containsExactly(10L, 20L, 30L);
        }

        @Test
        void emptyAndNullReturnEmptyNewList() {
            assertThat(strategy.rank(List.of())).isNotNull().isEmpty();
            assertThat(strategy.rank(null)).isNotNull().isEmpty();
        }

        @Test
        void doesNotMutateInput() {
            MatchResult a = result(10L, 1.0, Set.of());
            MatchResult b = result(20L, 5.0, Set.of());
            List<MatchResult> input = new ArrayList<>(List.of(a, b));

            List<MatchResult> ranked = strategy.rank(input);

            assertThat(input).containsExactly(a, b);
            assertThat(ranked).isNotSameAs(input);
        }
    }

    @Nested
    class SkillStrict {

        private final RankingStrategy strategy = new SkillStrictStrategy();

        @Test
        void ordersByFewestMissingSkillsFirst() {
            List<MatchResult> ranked = strategy.rank(List.of(
                    result(10L, 9.0, Set.of("a", "b", "c")),
                    result(20L, 1.0, Set.of()),
                    result(30L, 5.0, Set.of("a"))));

            assertThat(ids(ranked)).containsExactly(20L, 30L, 10L);
        }

        @Test
        void equalMissingCountFallsBackToScoreDescending() {
            List<MatchResult> ranked = strategy.rank(List.of(
                    result(10L, 3.0, Set.of("a")),
                    result(20L, 7.0, Set.of("b")),
                    result(30L, 5.0, Set.of("c"))));

            assertThat(ids(ranked)).containsExactly(20L, 30L, 10L);
        }

        @Test
        void equalMissingAndScoreBreaksByOpportunityIdAscending() {
            List<MatchResult> ranked = strategy.rank(List.of(
                    result(30L, 4.0, Set.of("a")),
                    result(10L, 4.0, Set.of("b")),
                    result(20L, 4.0, Set.of("c"))));

            assertThat(ids(ranked)).containsExactly(10L, 20L, 30L);
        }

        @Test
        void nullMissingSkillsCountsAsZero() {
            List<MatchResult> ranked = strategy.rank(List.of(
                    result(10L, 1.0, Set.of("a")),
                    result(20L, 1.0, null)));

            assertThat(ids(ranked)).containsExactly(20L, 10L);
        }

        @Test
        void emptyAndNullReturnEmptyNewList() {
            assertThat(strategy.rank(List.of())).isNotNull().isEmpty();
            assertThat(strategy.rank(null)).isNotNull().isEmpty();
        }

        @Test
        void doesNotMutateInput() {
            MatchResult a = result(10L, 1.0, Set.of("a"));
            MatchResult b = result(20L, 1.0, Set.of());
            List<MatchResult> input = new ArrayList<>(Arrays.asList(a, b));

            List<MatchResult> ranked = strategy.rank(input);

            assertThat(input).containsExactly(a, b);
            assertThat(ranked).isNotSameAs(input);
        }
    }

    @Nested
    class BeginnerFriendly {

        private final RankingStrategy strategy = new BeginnerFriendlyStrategy();

        @Test
        void ordersByScoreDescending() {
            List<MatchResult> ranked = strategy.rank(List.of(
                    result(10L, 2.0, Set.of()),
                    result(20L, 8.0, Set.of()),
                    result(30L, 5.0, Set.of())));

            assertThat(ids(ranked)).containsExactly(20L, 30L, 10L);
        }

        @Test
        void breaksScoreTiesByOpportunityIdAscending() {
            List<MatchResult> ranked = strategy.rank(List.of(
                    result(30L, 1.0, Set.of()),
                    result(10L, 1.0, Set.of())));

            assertThat(ids(ranked)).containsExactly(10L, 30L);
        }

        @Test
        void emptyAndNullReturnEmptyNewList() {
            assertThat(strategy.rank(List.of())).isNotNull().isEmpty();
            assertThat(strategy.rank(null)).isNotNull().isEmpty();
        }

        @Test
        void doesNotMutateInput() {
            MatchResult a = result(10L, 2.0, Set.of());
            MatchResult b = result(20L, 8.0, Set.of());
            List<MatchResult> input = new ArrayList<>(List.of(a, b));

            List<MatchResult> ranked = strategy.rank(input);

            assertThat(input).containsExactly(a, b);
            assertThat(ranked).isNotSameAs(input);
        }
    }
}
