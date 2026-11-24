package com.researchmatch.adapters;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Imports a faculty member's publication areas from OpenAlex.
 *
 * <p>TODO (Member 3): implement {@link #importEvidence(String)} using the faculty's OpenAlex id.
 * Return an empty list for an unknown id; do not invent areas.
 */
@Component
public class OpenAlexPublicationAdapter implements ResearchDataAdapter {

    public static final String PLATFORM = "openalex";

    @Override
    public String platformName() {
        return PLATFORM;
    }

    @Override
    public List<ImportedEvidence> importEvidence(String handle) {
        throw new UnsupportedOperationException("TODO (Member 3): OpenAlexPublicationAdapter.importEvidence");
    }
}
