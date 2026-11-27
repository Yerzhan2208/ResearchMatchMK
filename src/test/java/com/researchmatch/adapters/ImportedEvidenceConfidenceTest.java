package com.researchmatch.adapters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single confidence-rounding policy shared by both adapters (issue #27). These cases pin the
 * behaviour the adapters previously computed with duplicated private helpers.
 */
class ImportedEvidenceConfidenceTest {

    @Nested
    @DisplayName("confidenceFrom rounds to two decimals")
    class Rounding {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                // the values the adapters actually produce
                "0.6666666666666666, 0.67",
                "0.3333333333333333, 0.33",
                "0.5,                0.50",
                "0.17,               0.17",
                "1.0,                1.00",
                "0.0,                0.00",
                // ties and near-ties
                "0.125,              0.13",
                "0.124,              0.12",
                "0.994,              0.99",
                "0.996,              1.00"
        })
        void roundsToTwoDecimals(double raw, double expected) {
            assertThat(ImportedEvidence.confidenceFrom(raw)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("confidenceFrom clamps to the legal confidence range")
    class Clamping {

        @ParameterizedTest(name = "{0} -> 0.0")
        @ValueSource(doubles = {-0.5, -1.0, -0.0001})
        void negativeValuesClampToZero(double raw) {
            assertThat(ImportedEvidence.confidenceFrom(raw)).isZero();
        }

        @ParameterizedTest(name = "{0} -> 1.0")
        @ValueSource(doubles = {1.0001, 2.0, 42.0})
        void valuesAboveOneClampToOne(double raw) {
            assertThat(ImportedEvidence.confidenceFrom(raw)).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("a clamped result is always accepted by the canonical constructor")
    void clampedResultSatisfiesTheRecordInvariant() {
        // Without clamping these would throw IllegalArgumentException from the compact constructor.
        assertThat(new ImportedEvidence("Java", "repo", "snippet", ImportedEvidence.confidenceFrom(3.0))
                .confidence()).isEqualTo(1.0);
        assertThat(new ImportedEvidence("Java", "repo", "snippet", ImportedEvidence.confidenceFrom(-3.0))
                .confidence()).isZero();
    }

    @Test
    @DisplayName("both adapters report confidence through the same policy")
    void adaptersAgreeOnTheSharedPolicy() {
        // 2 of 3 -> 0.67 from each adapter: previously two separate, identical implementations.
        var github = GitHubSkillAdapter.deriveEvidence("someone", java.util.List.of(
                new GitHubSkillAdapter.Repo("a", "Java", java.util.List.of()),
                new GitHubSkillAdapter.Repo("b", "Java", java.util.List.of()),
                new GitHubSkillAdapter.Repo("c", "Rust", java.util.List.of())));

        var openalex = OpenAlexPublicationAdapter.deriveAreas("A1", java.util.List.of(
                new OpenAlexPublicationAdapter.Work("t1",
                        new OpenAlexPublicationAdapter.Topic("Java", null, null), null),
                new OpenAlexPublicationAdapter.Work("t2",
                        new OpenAlexPublicationAdapter.Topic("Java", null, null), null),
                new OpenAlexPublicationAdapter.Work("t3",
                        new OpenAlexPublicationAdapter.Topic("Rust", null, null), null)));

        double githubJava = github.stream()
                .filter(e -> e.skillName().equals("Java")).findFirst().orElseThrow().confidence();
        double openalexJava = openalex.stream()
                .filter(e -> e.skillName().equals("Java")).findFirst().orElseThrow().confidence();

        assertThat(githubJava).isEqualTo(0.67).isEqualTo(openalexJava);
        assertThat(githubJava).isEqualTo(ImportedEvidence.confidenceFrom(2.0 / 3.0));
    }
}
