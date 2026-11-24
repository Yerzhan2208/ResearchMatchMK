package com.researchmatch.adapters;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Imports a student's skills from a GitHub username.
 *
 * <p>TODO (Member 3): implement {@link #importEvidence(String)} (REST call + language/topic extraction).
 * Return an empty list for a handle with no public repositories; do not invent skills.
 */
@Component
public class GitHubSkillAdapter implements ResearchDataAdapter {

    public static final String PLATFORM = "github";

    @Override
    public String platformName() {
        return PLATFORM;
    }

    @Override
    public List<ImportedEvidence> importEvidence(String handle) {
        throw new UnsupportedOperationException("TODO (Member 3): GitHubSkillAdapter.importEvidence");
    }
}
