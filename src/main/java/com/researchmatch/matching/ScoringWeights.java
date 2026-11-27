package com.researchmatch.matching;

import com.researchmatch.repository.SystemConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves and validates the two scoring weights that combine the semantic and evidence layers into a
 * normalised {@code totalScore} in {@code [0, 1]}:
 *
 * <pre>
 *   totalScore = semanticWeight * semanticScore + evidenceWeight * evidenceScore
 * </pre>
 *
 * <p>Both component scores are normalised to {@code [0, 1]}, so requiring the weights to be non-negative and
 * to sum to {@code 1.0} guarantees the combined score stays in {@code [0, 1]}.
 *
 * <p>Invalid configuration never throws: it falls back to {@link #DEFAULT_SEMANTIC_WEIGHT} /
 * {@link #DEFAULT_EVIDENCE_WEIGHT} and logs a warning, matching {@link SystemConfigManager}'s house style of
 * "bad value ⇒ default". Resolution uses the public {@link SystemConfigManager#getDouble(String, double)}
 * API so the owning layer stays free of scoring policy.
 */
final class ScoringWeights {

    private static final Logger log = LoggerFactory.getLogger(ScoringWeights.class);

    /** Default semantic weight (issue #19: semantic is the headline signal, so it dominates). */
    static final double DEFAULT_SEMANTIC_WEIGHT = 0.70;

    /** Default evidence weight; semantic + evidence == 1.0. */
    static final double DEFAULT_EVIDENCE_WEIGHT = 0.30;

    /** Tolerance for the {@code semantic + evidence == 1} check against floating-point drift. */
    static final double SUM_TOLERANCE = 1e-9;

    final double semanticWeight;
    final double evidenceWeight;

    private ScoringWeights(double semanticWeight, double evidenceWeight) {
        this.semanticWeight = semanticWeight;
        this.evidenceWeight = evidenceWeight;
    }

    /**
     * Reads both weights from {@code config} and validates them. Any invalid combination
     * (negative weight, or a sum that is not {@code 1.0}) is replaced by the defaults as a pair, so the
     * invariant {@code semantic + evidence == 1.0} always holds.
     */
    static ScoringWeights resolve(SystemConfigManager config) {
        SystemConfigManager source = config == null ? SystemConfigManager.getInstance() : config;
        double semantic = source.getDouble(SystemConfigManager.KEY_SEMANTIC_WEIGHT, DEFAULT_SEMANTIC_WEIGHT);
        double evidence = source.getDouble(SystemConfigManager.KEY_EVIDENCE_WEIGHT, DEFAULT_EVIDENCE_WEIGHT);
        return validated(semantic, evidence);
    }

    /** Package-visible core of {@link #resolve(SystemConfigManager)} so validation is unit-testable. */
    static ScoringWeights validated(double semantic, double evidence) {
        if (semantic < 0.0 || evidence < 0.0
                || !Double.isFinite(semantic) || !Double.isFinite(evidence)
                || Math.abs(semantic + evidence - 1.0) > SUM_TOLERANCE) {
            log.warn("Invalid scoring weights (semantic={}, evidence={}); using defaults {}/{}",
                    semantic, evidence, DEFAULT_SEMANTIC_WEIGHT, DEFAULT_EVIDENCE_WEIGHT);
            return new ScoringWeights(DEFAULT_SEMANTIC_WEIGHT, DEFAULT_EVIDENCE_WEIGHT);
        }
        return new ScoringWeights(semantic, evidence);
    }
}
