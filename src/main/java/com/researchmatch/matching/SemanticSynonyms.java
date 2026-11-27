package com.researchmatch.matching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Expands common research/CS abbreviations and short forms to their canonical phrase before tokenising, so
 * that {@link TfIdfSemanticSimilarity} rewards semantic overlap rather than only literal word matches.
 *
 * <p>For example {@code "ml"} becomes {@code "machine learning"} and {@code "hci"} becomes
 * {@code "human computer interaction"}, letting a student interested in "Machine Learning" match an
 * opportunity that lists the {@code ML} skill. Hyphenated/slashed tokens are also split (e.g.
 * {@code "human-computer"} → {@code "human computer"}), so compound interests are not lost.
 *
 * <p>The mapping is intentionally small and data-driven; unknown tokens pass through unchanged. Fully
 * deterministic and offline — no external resources.
 */
final class SemanticSynonyms {

    /** Single-word aliases expanded to a canonical phrase (lower-case keys). */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("nlp", "natural language processing");
        ALIASES.put("ml", "machine learning");
        ALIASES.put("ai", "artificial intelligence");
        ALIASES.put("cv", "computer vision");
        ALIASES.put("db", "database");
        ALIASES.put("dbs", "database");
        ALIASES.put("hci", "human computer interaction");
        ALIASES.put("ui", "user interface");
        ALIASES.put("ux", "user experience");
        ALIASES.put("ir", "information retrieval");
        ALIASES.put("dl", "deep learning");
        ALIASES.put("os", "operating system");
    }

    private static final Pattern WORD_SPLIT = Pattern.compile("[^a-z0-9]+");

    private SemanticSynonyms() {
    }

    /**
     * Tokenises {@code text} (lower-cased, split on non-alphanumerics) and rewrites any alias token into its
     * canonical phrase tokens. Null/blank input yields an empty list.
     *
     * @return ordered tokens with aliases expanded; never null
     */
    static List<String> expand(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        String normalised = text.toLowerCase(Locale.ROOT).trim();
        if (normalised.isEmpty()) {
            return tokens;
        }
        for (String raw : WORD_SPLIT.split(normalised)) {
            if (raw.isEmpty()) {
                continue;
            }
            String canonical = ALIASES.get(raw);
            if (canonical == null) {
                tokens.add(raw);
            } else {
                for (String part : canonical.split(" ")) {
                    if (!part.isEmpty()) {
                        tokens.add(part);
                    }
                }
            }
        }
        return tokens;
    }
}
