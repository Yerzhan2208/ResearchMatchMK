package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.SystemConfigManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.AbstractList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Direct tests for {@link TfIdfSemanticSimilarity} and the package-private strategy-injection constructor of
 * {@link SemanticMatchHandler}. These pin the extracted TF-IDF behaviour (identical to the former inline
 * implementation) and cover the branches that the handler-level tests only reach indirectly.
 */
class TfIdfSemanticSimilarityTest {

    private final TfIdfSemanticSimilarity similarity = new TfIdfSemanticSimilarity();

    @Test
    void nameIsStable() {
        assertThat(similarity.name()).isEqualTo(TfIdfSemanticSimilarity.NAME);
        assertThat(TfIdfSemanticSimilarity.NAME).isEqualTo("tfidf");
    }

    // ---------------------------------------------------------------- similarity

    @Test
    void identicalTextScoresOne() {
        assertThat(similarity.similarity("java spring", "java spring")).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void overlappingTextScoresHigherThanUnrelated() {
        double overlapping = similarity.similarity("java spring", "spring boot java services");
        double unrelated = similarity.similarity("java spring", "coral reef fieldwork");
        assertThat(overlapping).isGreaterThan(unrelated);
        assertThat(unrelated).isEqualTo(0.0);
    }

    @Test
    void emptyAndNullInputsYieldZero() {
        assertThat(similarity.similarity(null, "java")).isEqualTo(0.0);
        assertThat(similarity.similarity("java", null)).isEqualTo(0.0);
        assertThat(similarity.similarity("", "java")).isEqualTo(0.0);
        assertThat(similarity.similarity("   ", "java")).isEqualTo(0.0);
        assertThat(similarity.similarity(null, null)).isEqualTo(0.0);
    }

    @Test
    void similarityIsWithinUnitInterval() {
        assertThat(similarity.similarity("python", "python ml models")).isBetween(0.0, 1.0);
    }

    @Test
    void repeatedTokensDoNotEscapeUnitInterval() {
        assertThat(similarity.similarity("java java java", "java")).isBetween(0.0, 1.0);
    }

    // ---------------------------------------------------------------- tokenize branches

    @Test
    void tokenizeSplitsAndLowercases() {
        assertThat(similarity.tokenize("Java-Spring_Boot!!")).containsExactly("java", "spring", "boot");
    }

    @Test
    void tokenizeNullAndBlankYieldEmpty() {
        assertThat(similarity.tokenize(null)).isEmpty();
        assertThat(similarity.tokenize("   ")).isEmpty();
        assertThat(similarity.tokenize("")).isEmpty();
    }

    @Test
    void tokenizeSkipsEmptySegments() {
        // Leading/trailing/repeated separators produce empty segments that must be skipped.
        assertThat(similarity.tokenize("  a--b  c ")).containsExactly("a", "b", "c");
    }

    // ---------------------------------------------------------------- cosine zero-magnitude guard

    @Test
    void cosineZeroWhenEitherSideEmpty() {
        assertThat(similarity.cosineSimilarity(List.of(), List.of("java"))).isEqualTo(0.0);
        assertThat(similarity.cosineSimilarity(List.of("java"), List.of())).isEqualTo(0.0);
    }

    @Test
    void cosineZeroWhenVectorMagnitudeIsZero() {
        // A list that reports non-empty but yields no elements bypasses the isEmpty() short-circuit and
        // exercises the zero-magnitude guard.
        List<String> reportedNonEmptyButEmpty = new AbstractList<>() {
            @Override
            public String get(int index) {
                throw new IndexOutOfBoundsException();
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }
        };
        assertThat(similarity.cosineSimilarity(reportedNonEmptyButEmpty, List.of("java"))).isEqualTo(0.0);
    }

    @Test
    void cosineZeroWhenOpportunityMagnitudeIsZero() {
        // Mirror of the student-side guard: the second operand of the short-circuit must also be exercised.
        List<String> reportedNonEmptyButEmpty = new AbstractList<>() {
            @Override
            public String get(int index) {
                throw new IndexOutOfBoundsException();
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }
        };
        assertThat(similarity.cosineSimilarity(List.of("java"), reportedNonEmptyButEmpty)).isEqualTo(0.0);
    }

    @Test
    void tokenizeSkipsLeadingEmptySegmentFromSplit() {
        // A non-alphanumeric lead produces an empty segment from String.split that must be skipped.
        assertThat(similarity.tokenize("!java")).containsExactly("java");
    }

    // ---------------------------------------------------------------- handler strategy injection

    @Test
    void handlerUsesInjectedStrategyAndScalesByWeight() {
        double weight = 0.75;
        SystemConfigManager mocked = Mockito.mock(SystemConfigManager.class);
        Mockito.when(mocked.getDouble(eq(SystemConfigManager.KEY_SEMANTIC_WEIGHT), anyDouble())).thenReturn(weight);
        // Keep semantic + evidence == 1 so weight resolution doesn't fall back to defaults.
        Mockito.when(mocked.getDouble(eq(SystemConfigManager.KEY_EVIDENCE_WEIGHT), anyDouble()))
                .thenReturn(1.0 - weight);

        SemanticSimilarity fixed = new SemanticSimilarity() {
            @Override
            public double similarity(String studentText, String opportunityText) {
                return 0.5;
            }

            @Override
            public String name() {
                return "fixed";
            }
        };

        StudentProfile student = new StudentProfile();
        student.setInterests(Set.of("java"));
        Opportunity opportunity = new Opportunity();
        opportunity.setTitle("java");

        SemanticMatchHandler handler = new SemanticMatchHandler(fixed, mocked);
        MatchContext context = new MatchContext(student, opportunity);
        assertThat(handler.evaluate(context)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
        assertThat(context.getLayerScores().get(SemanticMatchHandler.LAYER)).isCloseTo(0.5 * weight,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void handlerInjectionDefaultsNullStrategyToTfIdfAndNullConfigToSingleton() {
        StudentProfile student = new StudentProfile();
        student.setClaimedSkills(Set.of("java"));
        Opportunity opportunity = new Opportunity();
        opportunity.setTitle("java spring");

        SemanticMatchHandler handler = new SemanticMatchHandler(null, null);
        MatchContext context = new MatchContext(student, opportunity);
        assertThat(handler.evaluate(context)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
        assertThat(context.getLayerScores().get(SemanticMatchHandler.LAYER)).isGreaterThan(0.0);
    }
}
