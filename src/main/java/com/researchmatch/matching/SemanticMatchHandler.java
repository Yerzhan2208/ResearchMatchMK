package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.SystemConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scores interest / description similarity using offline TF-IDF + cosine similarity and records the
 * weighted contribution on the context.
 *
 * <p>Text sources:
 * <ul>
 *   <li>Student: interests, claimed skills, verified skills.</li>
 *   <li>Opportunity: title, description.</li>
 * </ul>
 *
 * <p>The similarity is computed over the two-document corpus (student vs. opportunity), so it is fully
 * deterministic and offline. The similarity in {@code [0, 1]} is scaled by
 * {@link SystemConfigManager#KEY_SEMANTIC_WEIGHT} and added via {@link MatchContext#addScore(String, double)}.
 */
@Component
public class SemanticMatchHandler extends AbstractMatchLayerHandler {

    public static final String LAYER = "semantic";

    static final double DEFAULT_SEMANTIC_WEIGHT = 0.30;

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");

    private final SystemConfigManager config;

    /** Spring injects the singleton configuration. */
    @Autowired
    public SemanticMatchHandler(SystemConfigManager config) {
        this.config = config == null ? SystemConfigManager.getInstance() : config;
    }

    /** No-arg form (tests/embedded): resolves the singleton configuration. */
    public SemanticMatchHandler() {
        this(SystemConfigManager.getInstance());
    }

    @Override
    public String layerName() {
        return LAYER;
    }

    @Override
    protected MatchLayerHandler.LayerResult apply(MatchContext context) {
        List<String> studentTokens = new ArrayList<>();
        studentTokens.addAll(tokenize(studentText(context.getStudent())));
        List<String> opportunityTokens = tokenize(opportunityText(context.getOpportunity()));

        double similarity = cosineSimilarity(studentTokens, opportunityTokens);
        double weight = config.getDouble(SystemConfigManager.KEY_SEMANTIC_WEIGHT, DEFAULT_SEMANTIC_WEIGHT);
        double contribution = similarity * weight;

        context.addScore(layerName(), contribution);
        return MatchLayerHandler.LayerResult.CONTINUE;
    }

    // ---------------------------------------------------------------- text assembly

    private static String studentText(StudentProfile student) {
        if (student == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendAll(sb, student.getInterests());
        appendAll(sb, student.getClaimedSkills());
        appendAll(sb, student.getVerifiedSkills());
        return sb.toString();
    }

    private static String opportunityText(Opportunity opportunity) {
        if (opportunity == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendText(sb, opportunity.getTitle());
        appendText(sb, opportunity.getDescription());
        return sb.toString();
    }

    private static void appendAll(StringBuilder sb, Set<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            appendText(sb, value);
        }
    }

    private static void appendText(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(value);
    }

    // ---------------------------------------------------------------- tokenising

    private static List<String> tokenize(String text) {
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
    private static double cosineSimilarity(List<String> studentTokens, List<String> opportunityTokens) {
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
