package com.researchmatch.adapters;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResearchMatchException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Imports a faculty member's publication areas from OpenAlex.
 *
 * <p><strong>Two modes.</strong> When {@code researchmatch.openalex.enabled=true} the adapter calls the public
 * OpenAlex API ({@code /works?filter=author.id:{id}}) and folds each work's primary topic / concepts into a
 * research area. Otherwise it runs in a <em>deterministic offline mode</em>: no network access, and a stable
 * rule-based area profile derived from the OpenAlex id, so demos and CI behave identically everywhere.
 *
 * <p>Areas are normalised to title case and de-duplicated case-insensitively, so OpenAlex's
 * {@code "natural language processing"} and {@code "Natural Language Processing"} never appear twice.
 *
 * <p>Nothing is ever invented: an unknown or empty author yields an empty list.
 */
@Component
public class OpenAlexPublicationAdapter implements ResearchDataAdapter {

    public static final String PLATFORM = "openalex";

    /** System property / config key that switches the adapter to live OpenAlex calls. */
    public static final String ENABLED_PROPERTY = "researchmatch.openalex.enabled";

    /** Offline mode reports at most this many research areas. */
    static final int MAX_OFFLINE_AREAS = 4;

    /** Areas the offline profile can report. */
    static final List<String> OFFLINE_AREAS = List.of(
            "Natural Language Processing", "Data Mining", "Computer Vision",
            "Software Engineering", "Human-Computer Interaction", "Machine Learning");

    private static final String OPENALEX_API = "https://api.openalex.org";

    /** OpenAlex author ids look like {@code A1234567890} or a full {@code https://openalex.org/A...} URL. */
    private static final String AUTHOR_ID_PREFIX = "A";

    private final RestClient restClient;

    /** {@code true} calls the live OpenAlex API; the default is the deterministic offline mode. */
    @Value("${" + ENABLED_PROPERTY + ":false}")
    private boolean online;

    /** Optional contact e-mail for the OpenAlex polite pool. */
    @Value("${researchmatch.openalex.mailto:}")
    private String mailto = "";

    /**
     * Spring constructor. Stays no-arg constructible (the contract signature); configuration is injected
     * into fields and the HTTP client is built internally.
     */
    public OpenAlexPublicationAdapter() {
        this.restClient = RestClient.builder().baseUrl(OPENALEX_API).build();
    }

    /**
     * Testable constructor.
     *
     * @param online     whether to use the live API
     * @param mailto     optional polite-pool contact; null/blank is ignored
     * @param restClient client used only in online mode
     */
    public OpenAlexPublicationAdapter(boolean online, String mailto, RestClient restClient) {
        this.online = online;
        this.mailto = mailto == null ? "" : mailto.trim();
        this.restClient = restClient;
    }

    @Override
    public String platformName() {
        return PLATFORM;
    }

    /** True when the adapter never touches the network. */
    public boolean isOfflineMode() {
        return !online;
    }

    /**
     * @param handle OpenAlex author id, e.g. {@code A1234567890}
     * @return never null; empty when the author is unknown or has no works
     * @throws InvalidRequestException if {@code handle} is blank or not an OpenAlex author id
     * @throws ResearchMatchException  if online mode is on and the payload cannot be interpreted
     */
    @Override
    public List<ImportedEvidence> importEvidence(String handle) {
        String authorId = normaliseAuthorId(handle);
        if (isOfflineMode()) {
            return offlineAreas(authorId);
        }
        return remoteAreas(authorId);
    }

    // ------------------------------------------------------------------ offline mode

    /**
     * Deterministic, network-free area profile: the same author id always yields the same areas, in a stable
     * order, with strictly decreasing confidence.
     */
    List<ImportedEvidence> offlineAreas(String authorId) {
        int hash = Math.abs(authorId.hashCode());
        if (hash % 11 == 0) {
            // Models an author with no indexed works: nothing is invented.
            return List.of();
        }
        int count = 1 + (hash % MAX_OFFLINE_AREAS);
        int offset = hash % OFFLINE_AREAS.size();

        List<ImportedEvidence> evidence = new ArrayList<>(count);
        for (int i = 0; i < count && i < OFFLINE_AREAS.size(); i++) {
            String area = OFFLINE_AREAS.get((offset + i) % OFFLINE_AREAS.size());
            double confidence = round(1.0 - (i * 0.2));
            evidence.add(new ImportedEvidence(
                    area,
                    "openalex:" + authorId,
                    "Offline sample: the author's indexed works are tagged " + area,
                    confidence));
        }
        return evidence;
    }

    private static double round(double value) {
        return Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------ online mode

    private List<ImportedEvidence> remoteAreas(String authorId) {
        WorkPage page;
        try {
            page = restClient.get()
                    .uri(uri -> {
                        var builder = uri.path("/works")
                                .queryParam("filter", "author.id:" + authorId)
                                .queryParam("per-page", 50);
                        if (!mailto.isEmpty()) {
                            builder.queryParam("mailto", mailto);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(WorkPage.class);
        } catch (RuntimeException e) {
            throw new ResearchMatchException("OpenAlex lookup failed for " + authorId, e);
        }
        if (page == null || page.results() == null) {
            return List.of();
        }
        return deriveAreas(authorId, page.results());
    }

    /**
     * Folds an OpenAlex {@code /works} payload into research areas, ranked by how many works carry each area.
     *
     * <p>Precedence per work: {@code primary_topic.display_name}, then {@code primary_topic.subfield},
     * then {@code primary_topic.field}, then the first entry of {@code concepts}. A work with none of those
     * contributes nothing.
     *
     * @param authorId owner of the works, used for the evidence snippet
     * @param works    work payloads; null entries are skipped
     */
    public static List<ImportedEvidence> deriveAreas(String authorId, List<Work> works) {
        // lowercase key -> first spelling seen, so OpenAlex casing variants count together.
        Map<String, String> canonicalByKey = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;

        for (Work work : works) {
            if (work == null) {
                continue;
            }
            String area = areaOf(work);
            if (area == null) {
                continue;
            }
            total++;
            String key = area.toLowerCase(Locale.ROOT);
            String canonical = canonicalByKey.computeIfAbsent(key, k -> area);
            counts.merge(canonical, 1, Integer::sum);
        }
        if (total == 0) {
            return List.of();
        }
        final int workCount = total;

        List<ImportedEvidence> evidence = new ArrayList<>(counts.size());
        counts.forEach((area, count) -> evidence.add(new ImportedEvidence(
                area,
                "openalex:" + authorId,
                count + " of " + workCount + " indexed works are tagged " + area,
                round((double) count / workCount))));
        return evidence;
    }

    private static String areaOf(Work work) {
        Topic topic = work.primaryTopic();
        if (topic != null) {
            String candidate = firstNonBlank(topic.displayName(), topic.subfield(), topic.field());
            if (candidate != null) {
                return titleCase(candidate);
            }
        }
        if (work.concepts() != null) {
            for (Concept concept : work.concepts()) {
                if (concept != null && concept.displayName() != null && !concept.displayName().isBlank()) {
                    return titleCase(concept.displayName());
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String titleCase(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private static String normaliseAuthorId(String handle) {
        if (handle == null || handle.isBlank()) {
            throw new InvalidRequestException("An OpenAlex author id is required for the import");
        }
        String trimmed = handle.trim();
        int slash = trimmed.lastIndexOf('/');
        if (slash >= 0 && slash < trimmed.length() - 1) {
            trimmed = trimmed.substring(slash + 1);
        }
        if (!trimmed.startsWith(AUTHOR_ID_PREFIX) || trimmed.length() < 2) {
            throw new InvalidRequestException(
                    "OpenAlex author id must start with '" + AUTHOR_ID_PREFIX + "', e.g. A1234567890, but was: "
                            + trimmed);
        }
        return trimmed;
    }

    /** OpenAlex {@code /works} response envelope; only the fields used here are projected. */
    public record WorkPage(@JsonProperty("results") List<Work> results) {
    }

    /** One OpenAlex work. */
    public record Work(@JsonProperty("title") String title,
                       @JsonProperty("primary_topic") Topic primaryTopic,
                       @JsonProperty("concepts") List<Concept> concepts) {
    }

    /** OpenAlex primary topic block. */
    public record Topic(@JsonProperty("display_name") String displayName,
                        @JsonProperty("subfield") String subfield,
                        @JsonProperty("field") String field) {
    }

    /** Legacy OpenAlex concept block, still returned on many works. */
    public record Concept(@JsonProperty("display_name") String displayName) {
    }
}
