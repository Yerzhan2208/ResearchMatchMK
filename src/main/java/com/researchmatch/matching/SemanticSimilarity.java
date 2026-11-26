package com.researchmatch.matching;

/**
 * Strategy for computing the semantic similarity of a student–opportunity text pair.
 *
 * <p>Implementations are stateless and thread-safe. {@link #similarity(String, String)} returns a value in
 * {@code [0, 1]} (implementation-defined clamping) and must never throw for ordinary data conditions: null or
 * empty input yields {@code 0.0}. The {@link SemanticMatchHandler} multiplies the result by
 * {@code matching.semanticWeight} before recording it, so similarity strategies do not apply weights.
 */
public interface SemanticSimilarity {

    /**
     * @param studentText     concatenated student interest/skill text; may be null or blank
     * @param opportunityText concatenated opportunity title/description text; may be null or blank
     * @return similarity in {@code [0, 1]}; {@code 0.0} for empty input
     */
    double similarity(String studentText, String opportunityText);

    /** Stable name used for diagnostics, e.g. {@code "tfidf"} or {@code "embedding"}. */
    String name();
}
