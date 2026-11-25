package com.researchmatch.matching;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the null-guard branches of {@link SemanticMatchHandler}'s private text helpers. These guards protect
 * against null inputs that the public handler path (which always supplies non-null entities via
 * {@link MatchContext}) cannot produce, so they are exercised directly here rather than through the chain.
 */
class SemanticMatchHandlerNullGuardTest {

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = SemanticMatchHandler.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @Test
    void studentText_nullReturnsEmpty() throws Exception {
        assertThat((String) invoke("studentText", new Class<?>[]{com.researchmatch.models.StudentProfile.class},
                new Object[]{null})).isEmpty();
    }

    @Test
    void opportunityText_nullReturnsEmpty() throws Exception {
        assertThat((String) invoke("opportunityText", new Class<?>[]{com.researchmatch.models.Opportunity.class},
                new Object[]{null})).isEmpty();
    }

    @Test
    void tokenize_nullReturnsEmptyList() throws Exception {
        Object tokens = invoke("tokenize", new Class<?>[]{String.class}, new Object[]{null});
        assertThat((java.util.List<?>) tokens).isEmpty();
    }

    @Test
    void cosineSimilarity_zeroMagnitudeReturnsZero() throws Exception {
        // A list that reports non-empty but yields no elements makes termFrequencies empty while bypassing
        // the isEmpty() short-circuit, exercising the zero-magnitude guard. The guard is defensive: ordinary
        // token lists always have positive magnitude once non-empty.
        java.util.List<String> reportedNonEmptyButEmpty = new java.util.AbstractList<>() {
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
        Object result = invoke("cosineSimilarity",
                new Class<?>[]{java.util.List.class, java.util.List.class},
                new Object[]{reportedNonEmptyButEmpty, java.util.List.of("java")});
        assertThat((Double) result).isEqualTo(0.0);
    }
}
