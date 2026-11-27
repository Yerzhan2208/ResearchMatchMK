package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.SystemConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Scores interest / description similarity and records the weighted contribution on the context.
 *
 * <p>Text sources:
 * <ul>
 *   <li>Student: interests, claimed skills, verified skills.</li>
 *   <li>Opportunity: title, description.</li>
 * </ul>
 *
 * <p>The raw similarity comes from an injected {@link SemanticSimilarity} strategy (TF-IDF by default). The
 * similarity in {@code [0, 1]} is scaled by {@link SystemConfigManager#KEY_SEMANTIC_WEIGHT} and added via
 * {@link MatchContext#addScore(String, double)}.
 */
@Component
public class SemanticMatchHandler extends AbstractMatchLayerHandler {

    public static final String LAYER = "semantic";

    static final double DEFAULT_SEMANTIC_WEIGHT = 0.30;

    private final SystemConfigManager config;
    private final SemanticSimilarity similarity;

    /** Spring injects the singleton configuration; defaults to the TF-IDF strategy. */
    @Autowired
    public SemanticMatchHandler(SystemConfigManager config) {
        this.config = config == null ? SystemConfigManager.getInstance() : config;
        this.similarity = new TfIdfSemanticSimilarity();
    }

    /** No-arg form (tests/embedded): resolves the singleton configuration. */
    public SemanticMatchHandler() {
        this(SystemConfigManager.getInstance());
    }

    /** Test/injection form: use the supplied strategy with the engine's configuration. */
    SemanticMatchHandler(SemanticSimilarity similarity, SystemConfigManager config) {
        this.config = config == null ? SystemConfigManager.getInstance() : config;
        this.similarity = similarity == null ? new TfIdfSemanticSimilarity() : similarity;
    }

    @Override
    public String layerName() {
        return LAYER;
    }

    @Override
    protected MatchLayerHandler.LayerResult apply(MatchContext context) {
        String studentText = studentText(context.getStudent());
        String opportunityText = opportunityText(context.getOpportunity());

        double similarityScore = similarity.similarity(studentText, opportunityText);
        double weight = config.getDouble(SystemConfigManager.KEY_SEMANTIC_WEIGHT, DEFAULT_SEMANTIC_WEIGHT);
        double contribution = similarityScore * weight;

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

    // ---------------------------------------------------------------- TF-IDF helpers (reflection/back-compat)

    /** Delegates to {@link TfIdfSemanticSimilarity}; retained for reflection-based coverage tests. */
    private static List<String> tokenize(String text) {
        return new TfIdfSemanticSimilarity().tokenize(text);
    }

    /** Delegates to {@link TfIdfSemanticSimilarity}; retained for reflection-based coverage tests. */
    private static double cosineSimilarity(List<String> studentTokens, List<String> opportunityTokens) {
        return new TfIdfSemanticSimilarity().cosineSimilarity(studentTokens, opportunityTokens);
    }
}
