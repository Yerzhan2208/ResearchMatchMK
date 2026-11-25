package com.researchmatch.adapters;

/**
 * Provider-neutral value produced by a {@link ResearchDataAdapter}.
 *
 * @param skillName       normalised skill or research area, e.g. {@code "Java"} or {@code "NLP"}
 * @param sourceName      repository or paper used as evidence
 * @param evidenceSnippet short human-readable justification; may be null
 * @param confidence      0.0–1.0 strength of the signal: adapters report the fraction of the author's
 *                        repositories / works that exhibit the skill or area
 */
public record ImportedEvidence(String skillName, String sourceName, String evidenceSnippet, double confidence) {

    public ImportedEvidence {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within [0, 1]");
        }
    }
}
