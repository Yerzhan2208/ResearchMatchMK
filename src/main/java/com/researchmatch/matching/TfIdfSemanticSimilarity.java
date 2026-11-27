package com.researchmatch.matching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Offline TF-IDF + cosine {@link SemanticSimilarity}. Fully deterministic, no external resources.
 *
 * <p>The similarity is computed over the two-document corpus (student vs. opportunity): IDF is smoothed over
 * just those two documents, so the result is stable and requires no model. Returns {@code 0.0} when either
 * side is empty or a vector has zero magnitude. This is the default and universal fallback strategy.
 */
public final class TfIdfSemanticSimilarity implements SemanticSimilarity {

    public static final String NAME = "tfidf";

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public double similarity(String studentText, String opportunityText) {
        List<String> studentTokens = tokenize(studentText);
        List<String> opportunityTokens = tokenize(opportunityText);
        return cosineSimilarity(studentTokens, opportunityTokens);
    }

    // ---------------------------------------------------------------- tokenising

    List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        String normalised = text.toLowerCase(Locale.ROOT).trim();
        if (normalised.isEmpty()) {
            return tokens;
        }
        for (String token : TOKEN_SPLIT.split(normalised)) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    // ---------------------------------------------------------------- TF-IDF + cosine

    /**
     * Cosine similarity between two token bags, weighted by IDF computed over the two documents.
     * Returns {@code 0.0} when either side is empty or a vector has zero magnitude.
     */
    double cosineSimilarity(List<String> studentTokens, List<String> opportunityTokens) {
        if (studentTokens.isEmpty() || opportunityTokens.isEmpty()) {
            return 0.0;
        }

        Map<String, Integer> studentTf = termFrequencies(studentTokens);
        Map<String, Integer> opportunityTf = termFrequencies(opportunityTokens);

        Set<String> vocabulary = new LinkedHashSet<>();
        vocabulary.addAll(studentTf.keySet());
        vocabulary.addAll(opportunityTf.keySet());

        int documentCount = 2;
        double dot = 0.0;
        double studentMagnitude = 0.0;
        double opportunityMagnitude = 0.0;

        for (String term : vocabulary) {
            double idf = idf(term, studentTf, opportunityTf, documentCount);
            double studentWeight = studentTf.getOrDefault(term, 0) * idf;
            double opportunityWeight = opportunityTf.getOrDefault(term, 0) * idf;

            dot += studentWeight * opportunityWeight;
            studentMagnitude += studentWeight * studentWeight;
            opportunityMagnitude += opportunityWeight * opportunityWeight;
        }

        if (studentMagnitude == 0.0 || opportunityMagnitude == 0.0) {
            return 0.0;
        }
        double similarity = dot / (Math.sqrt(studentMagnitude) * Math.sqrt(opportunityMagnitude));
        // Guard against floating-point overshoot just outside [0, 1].
        return Math.max(0.0, Math.min(1.0, similarity));
    }

    private static Map<String, Integer> termFrequencies(List<String> tokens) {
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokens) {
            tf.merge(token, 1, Integer::sum);
        }
        return tf;
    }

    /** Smoothed IDF: {@code ln((1 + N) / (1 + df)) + 1}. A term in both docs still carries weight. */
    private static double idf(String term, Map<String, Integer> studentTf,
                              Map<String, Integer> opportunityTf, int documentCount) {
        int documentFrequency = 0;
        if (studentTf.containsKey(term)) {
            documentFrequency++;
        }
        if (opportunityTf.containsKey(term)) {
            documentFrequency++;
        }
        return Math.log((1.0 + documentCount) / (1.0 + documentFrequency)) + 1.0;
    }
}
