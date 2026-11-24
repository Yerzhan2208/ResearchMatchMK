package com.researchmatch.config;

import com.researchmatch.adapters.ResearchDataAdapter;

import java.util.List;
import java.util.Optional;

/**
 * Read-only description of what a {@link ResearchDataAdapter} can ingest.
 *
 * <p>Shared contract so Member 2 (matching), Member 3 (adapters) and Member 4 (facade) agree on how
 * external data sources are addressed, without Member 4 injecting concrete adapter classes.
 *
 * <p>TODO (Member 3): annotate the implementation as a Spring {@code @Component} and register
 * {@code GitHubSkillAdapter} and {@code OpenAlexPublicationAdapter} with {@link #sources()}.
 */
public interface ResearchDataSourceRegistry {

    /** Logical source names, e.g. {@code "github"} and {@code "openalex"}. */
    List<ResearchDataAdapter> sources();

    /**
     * Finds the adapter matching {@code source} (case-insensitive), or empty when the source is unknown.
     *
     * @throws InvalidRequestException if {@code source} is null or blank
     */
    default Optional<ResearchDataAdapter> forSource(String source) {
        if (source == null || source.isBlank()) {
            throw new InvalidRequestException("Adapter source must not be blank");
        }
        String wanted = source.trim();
        return sources().stream()
                .filter(adapter -> adapter.platformName().equalsIgnoreCase(wanted))
                .findFirst();
    }
}
