package com.researchmatch.matching;

import com.researchmatch.repository.SystemConfigManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ScoringWeightsTest {

    @Test
    void resolveReadsBothConfiguredWeights() {
        SystemConfigManager config = Mockito.mock(SystemConfigManager.class);
        when(config.getDouble(eq(SystemConfigManager.KEY_SEMANTIC_WEIGHT), anyDouble())).thenReturn(0.6);
        when(config.getDouble(eq(SystemConfigManager.KEY_EVIDENCE_WEIGHT), anyDouble())).thenReturn(0.4);

        ScoringWeights w = ScoringWeights.resolve(config);

        assertThat(w.semanticWeight).isEqualTo(0.6);
        assertThat(w.evidenceWeight).isEqualTo(0.4);
    }

    @Test
    void resolveFallsBackToSingletonWhenConfigNull() {
        ScoringWeights w = ScoringWeights.resolve(null);

        assertThat(w.semanticWeight).isEqualTo(ScoringWeights.DEFAULT_SEMANTIC_WEIGHT);
        assertThat(w.evidenceWeight).isEqualTo(ScoringWeights.DEFAULT_EVIDENCE_WEIGHT);
    }

    @Test
    void validPairIsAccepted() {
        ScoringWeights w = ScoringWeights.validated(0.8, 0.2);

        assertThat(w.semanticWeight).isEqualTo(0.8);
        assertThat(w.evidenceWeight).isEqualTo(0.2);
    }

    @Test
    void negativeWeightFallsBackToDefaults() {
        ScoringWeights w = ScoringWeights.validated(-0.1, 1.1);

        assertThat(w.semanticWeight).isEqualTo(ScoringWeights.DEFAULT_SEMANTIC_WEIGHT);
        assertThat(w.evidenceWeight).isEqualTo(ScoringWeights.DEFAULT_EVIDENCE_WEIGHT);
    }

    @Test
    void sumNotEqualToOneFallsBackToDefaults() {
        ScoringWeights w = ScoringWeights.validated(0.5, 0.6);

        assertThat(w.semanticWeight).isEqualTo(ScoringWeights.DEFAULT_SEMANTIC_WEIGHT);
        assertThat(w.evidenceWeight).isEqualTo(ScoringWeights.DEFAULT_EVIDENCE_WEIGHT);
    }

    @Test
    void nonFiniteWeightFallsBackToDefaults() {
        ScoringWeights w = ScoringWeights.validated(Double.NaN, 1.0);

        assertThat(w.semanticWeight).isEqualTo(ScoringWeights.DEFAULT_SEMANTIC_WEIGHT);
        assertThat(w.evidenceWeight).isEqualTo(ScoringWeights.DEFAULT_EVIDENCE_WEIGHT);
    }

    @Test
    void floatingPointDriftWithinToleranceIsAccepted() {
        // 0.7 + 0.30000000000000004 is within SUM_TOLERANCE; must not fall back.
        ScoringWeights w = ScoringWeights.validated(0.7, 0.30000000000000004);

        assertThat(w.semanticWeight).isEqualTo(0.7);
        assertThat(w.evidenceWeight).isEqualTo(0.30000000000000004);
    }
}
