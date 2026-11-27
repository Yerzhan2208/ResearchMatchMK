package com.researchmatch.adapters;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResearchMatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Imports a student's skills from a GitHub username.
 *
 * <p><strong>Two modes.</strong> When {@code researchmatch.github.token} is configured the adapter calls the
 * public GitHub REST API ({@code /users/{handle}/repos}) and derives skills from repository languages and
 * topics, so the evidence comes from the account itself.
 *
 * <p>Without a token it runs offline and never touches the network. Offline it reports only the
 * <em>curated fixtures</em> listed in {@link #OFFLINE_FIXTURES} — canned sample profiles for a handful of
 * handles used by demos. <strong>Any other handle yields an empty list</strong>, which the contract defines as
 * "nothing found". Offline results are labelled as sample data ({@code fixture:} sources,
 * {@link ResearchDataAdapter#OFFLINE_SNIPPET_PREFIX} snippets) so they cannot be read as fetched evidence.
 *
 * <p>Live confidence is the fraction of the user's repositories that exhibit the skill
 * ({@code reposUsingSkill / repoCount}, clamped to {@code [0, 1]}), which is easy to explain and stable.
 *
 * <p>A user with no repositories yields an empty list; no evidence is ever inferred from a handle's spelling.
 */
@Component
public class GitHubSkillAdapter implements ResearchDataAdapter {

    public static final String PLATFORM = "github";

    /** System property / config key holding the optional GitHub token. */
    public static final String TOKEN_PROPERTY = "researchmatch.github.token";

    /** Offline mode reports at most this many skills per handle, strongest first. */
    static final int MAX_OFFLINE_SKILLS = 3;

    /**
     * Curated sample profiles used by offline mode, keyed by GitHub username. These are the only handles
     * offline mode answers for; any other handle yields an empty list. Keys are matched case-insensitively.
     *
     * <p>Every key is a student seeded by {@code config/DataSeeder}, so "Import from GitHub" works on a fresh
     * checkout. No real third-party account appears here: a fixture for a stranger's handle would be sample
     * data attributed to someone who never agreed to it, and its confidence figures would be invented. All of
     * this is fictional sample data, labelled as such by {@link ImportedEvidence#asFixture}.
     */
    static final Map<String, List<ImportedEvidence>> OFFLINE_FIXTURES = Map.of(
            "demo-mei-ling-ho", List.of(
                    new ImportedEvidence("Python", "demo-mei-ling-ho/nlp-coursework",
                            "tokeniser and POS-tagging exercises", 0.60),
                    new ImportedEvidence("Jupyter Notebook", "demo-mei-ling-ho/text-mining-notes",
                            "notebooks exploring text corpora", 0.40)),
            "demo-arjun-patel", List.of(
                    new ImportedEvidence("Python", "demo-arjun-patel/sklearn-practice",
                            "model-training scripts with scikit-learn", 0.50),
                    new ImportedEvidence("Pandas", "demo-arjun-patel/data-cleaning",
                            "tabular data-cleaning pipelines", 0.33),
                    new ImportedEvidence("HTML", "demo-arjun-patel/sklearn-practice",
                            "generated report pages", 0.17)),
            "demo-sze-wing-lau", List.of(
                    new ImportedEvidence("Java", "demo-sze-wing-lau/vision-lab",
                            "OpenCV wrapper written in Java", 0.67),
                    new ImportedEvidence("C++", "demo-sze-wing-lau/opencv-sandbox",
                            "image-filter experiments", 0.33)));

    private static final String GITHUB_API = "https://api.github.com";

    private final RestClient restClient;

    /** Optional GitHub token; blank (the default) selects the offline fixture mode. */
    @Value("${" + TOKEN_PROPERTY + ":}")
    private String token = "";

    /**
     * Spring constructor. Both adapters stay no-arg constructible (the contract signature); their optional
     * configuration is injected into fields and their HTTP client is built internally.
     */
    public GitHubSkillAdapter() {
        this.restClient = RestClient.builder().baseUrl(GITHUB_API).build();
    }

    /**
     * Testable constructor.
     *
     * @param token      GitHub token; null or blank selects the offline fixture mode
     * @param restClient client used only when a token is present
     */
    public GitHubSkillAdapter(String token, RestClient restClient) {
        this.token = token == null ? "" : token.trim();
        this.restClient = restClient;
    }

    @Override
    public String platformName() {
        return PLATFORM;
    }

    /** True when no token is configured, i.e. the adapter never touches the network. */
    public boolean isOfflineMode() {
        return token == null || token.trim().isEmpty();
    }

    /**
     * @param handle GitHub username
     * @return never null; empty when the user has no public repositories (or, offline, has no fixture)
     * @throws InvalidRequestException if {@code handle} is blank
     * @throws ResearchMatchException  if a token is configured but the API call or payload fails
     */
    @Override
    public List<ImportedEvidence> importEvidence(String handle) {
        String username = normaliseHandle(handle);
        if (isOfflineMode()) {
            return offlineEvidence(username);
        }
        return remoteEvidence(username);
    }

    // ------------------------------------------------------------------ offline mode

    /**
     * Returns the curated fixture for {@code username}, re-labelled as sample data, or an empty list when the
     * handle has no fixture. Nothing is derived from the handle itself.
     */
    List<ImportedEvidence> offlineEvidence(String username) {
        Map<String, List<ImportedEvidence>> byLowerHandle = new LinkedHashMap<>();
        OFFLINE_FIXTURES.forEach((handle, samples) -> byLowerHandle.put(handle.toLowerCase(Locale.ROOT), samples));
        return ImportedEvidence.forHandle(byLowerHandle, username.toLowerCase(Locale.ROOT)).stream()
                .limit(MAX_OFFLINE_SKILLS)
                .map(ImportedEvidence::asFixture)
                .toList();
    }

    private static double round(double value) {
        return Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------ remote mode

    private List<ImportedEvidence> remoteEvidence(String username) {
        Repo[] repos;
        try {
            repos = restClient.get()
                    .uri("/users/{username}/repos?per_page=100", username)
                    .header("Authorization", "Bearer " + token.trim())
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(Repo[].class);
        } catch (RuntimeException e) {
            throw new ResearchMatchException("GitHub lookup failed for " + username, e);
        }
        if (repos == null || repos.length == 0) {
            return List.of();
        }
        return deriveEvidence(username, List.of(repos));
    }

    /**
     * Turns repository metadata into evidence: one entry per language or topic, ranked by how many
     * repositories exhibit it.
     *
     * @param username owner of the repositories, used for the evidence snippet
     * @param repos    repository metadata; a repository may be null (skipped)
     */
    public static List<ImportedEvidence> deriveEvidence(String username, Collection<Repo> repos) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> firstRepo = new LinkedHashMap<>();
        int total = 0;

        for (Repo repo : repos) {
            if (repo == null) {
                continue;
            }
            total++;
            Set<String> skills = new LinkedHashSet<>();
            if (repo.language() != null && !repo.language().isBlank()) {
                skills.add(repo.language().trim());
            }
            if (repo.topics() != null) {
                for (String topic : repo.topics()) {
                    if (topic != null && !topic.isBlank()) {
                        skills.add(titleCase(topic.trim()));
                    }
                }
            }
            for (String skill : skills) {
                counts.merge(skill, 1, Integer::sum);
                firstRepo.putIfAbsent(skill, repo.name() == null ? username : repo.name());
            }
        }
        if (total == 0) {
            return List.of();
        }
        final int repoCount = total;

        List<ImportedEvidence> evidence = new ArrayList<>(counts.size());
        counts.forEach((skill, count) -> evidence.add(new ImportedEvidence(
                skill,
                firstRepo.get(skill),
                "Found in " + count + " of " + repoCount + " public repositories of " + username,
                round((double) count / repoCount))));
        return evidence;
    }

    private static String titleCase(String raw) {
        if (raw.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static String normaliseHandle(String handle) {
        if (handle == null || handle.isBlank()) {
            throw new InvalidRequestException("A GitHub handle is required for the import");
        }
        String trimmed = handle.trim();
        // Accept full profile URLs as pasted by users.
        if (trimmed.contains("github.com/")) {
            trimmed = trimmed.substring(trimmed.indexOf("github.com/") + "github.com/".length());
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            throw new InvalidRequestException("A GitHub handle is required for the import");
        }
        return trimmed;
    }

    /** Minimal projection of a GitHub repository payload; unknown fields are ignored by Jackson. */
    public record Repo(String name, String language, List<String> topics) {
    }
}
