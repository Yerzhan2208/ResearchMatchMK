package com.researchmatch.adapters;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResearchMatchException;

import java.util.List;

/**
 * Adapter to an external research/skill data source (Member 3).
 *
 * <p>Implementations translate the provider's payload into ResearchMatch values; they never persist
 * anything and never call repositories.
 */
public interface ResearchDataAdapter {

    /**
     * Prefix stamped on the {@code evidenceSnippet} of any sample produced without a live lookup, so a
     * canned value can never be read as imported evidence.
     */
    String OFFLINE_SNIPPET_PREFIX = "Offline fixture (sample data, not fetched): ";

    /**
     * Prefix stamped on the {@code sourceName} of any sample produced without a live lookup. Callers that
     * must not treat sample data as verified evidence (e.g. {@code ResearchMatchFacade#importSkills}) test
     * for this prefix.
     */
    String FIXTURE_SOURCE_PREFIX = "fixture:";

    /** Logical, lowercase source name, e.g. {@code "github"}. Stable — used as a lookup key. */
    String platformName();

    /**
     * Imports the skills/evidence observable for {@code handle} (a GitHub username or OpenAlex id).
     *
     * @param handle never blank
     * @return never null, possibly empty; never contains null elements
     * @throws InvalidRequestException  if {@code handle} is blank (or malformed for the platform)
     * @throws ResearchMatchException   if the upstream payload cannot be interpreted; implementations that
     *                                  work offline never raise this
     */
    List<ImportedEvidence> importEvidence(String handle);
}
