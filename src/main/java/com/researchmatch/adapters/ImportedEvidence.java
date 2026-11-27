package com.researchmatch.adapters;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * Returns the fixture recorded under {@code handle}, or an empty list when there is none.
     *
     * <p>Used by the adapters' offline mode. Lookups are case-insensitive and trimmed. Callers map the
     * result through {@link #asFixture(ImportedEvidence)} so every offline entry is labelled as sample data
     * rather than reported as real evidence.
     *
     * @param fixtures fixtures keyed by the exact handle they describe
     * @param handle   already-validated handle; the adapters reject blank input before reaching here
     */
    static List<ImportedEvidence> forHandle(Map<String, List<ImportedEvidence>> fixtures, String handle) {
        return Optional.ofNullable(fixtures.get(handle.trim())).orElse(List.of());
    }

    /**
     * Re-labels a curated fixture sample for offline display: {@code sourceName} is prefixed with
     * {@code fixture:} and {@code evidenceSnippet} with {@link ResearchDataAdapter#OFFLINE_SNIPPET_PREFIX},
     * so neither the profile page nor a later reader can mistake a canned sample for imported evidence.
     */
    static ImportedEvidence asFixture(ImportedEvidence sample) {
        return new ImportedEvidence(
                sample.skillName(),
                ResearchDataAdapter.FIXTURE_SOURCE_PREFIX + sample.sourceName(),
                ResearchDataAdapter.OFFLINE_SNIPPET_PREFIX + sample.evidenceSnippet(),
                sample.confidence());
    }

    /** True when this entry is unverified sample data produced without a live lookup. */
    public boolean isSampleData() {
        return sourceName != null && sourceName.startsWith(ResearchDataAdapter.FIXTURE_SOURCE_PREFIX);
    }
}
