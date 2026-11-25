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
 * topics. Without a token it runs in a <em>deterministic offline mode</em>: no network access at all, and a
 * stable rule-based profile derived from the handle, so demos and CI behave identically on every machine.
 *
 * <p>Confidence is the fraction of the user's repositories that exhibit the skill
 * ({@code reposUsingSkill / repoCount}, clamped to {@code [0, 1]}), which is easy to explain and stable.
 *
 * <p>Nothing is ever invented: a user with no repositories yields an empty list.
 */
@Component
public class GitHubSkillAdapter implements ResearchDataAdapter {

    public static final String PLATFORM = "github";

    /** System property / config key holding the optional GitHub token. */
    public static final String TOKEN_PROPERTY = "researchmatch.github.token";

    /** Offline mode reports at most this many skills, strongest first. */
    static final int MAX_OFFLINE_SKILLS = 3;

    /** Languages the offline profile can report, most common first. */
    static final List<String> OFFLINE_LANGUAGES = List.of("Java", "Python", "JavaScript", "TypeScript", "SQL");

    private static final String GITHUB_API = "https://api.github.com";

    private final RestClient restClient;

    /** Optional GitHub token; blank (the default) selects the deterministic offline mode. */
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
     * @param token      GitHub token; null or blank selects the deterministic offline mode
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
     * @return never null; empty when the user has no public repositories
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
     * Deterministic, network-free profile. The same handle always yields exactly the same evidence.
     *
     * <p>Rules: the handle's own name contributes any language it literally contains (so
     * {@code java-wizard} reports Java), otherwise a stable hash picks how many languages to report. The
     * confidence of the n-th reported skill strictly decreases, keeping the ordering meaningful.
     */
    List<ImportedEvidence> offlineEvidence(String username) {
        List<String> skills = new ArrayList<>();
        String lower = username.toLowerCase(Locale.ROOT);
        for (String language : OFFLINE_LANGUAGES) {
            if (lower.contains(language.toLowerCase(Locale.ROOT))) {
                skills.add(language);
            }
        }

        int hash = Math.abs(username.hashCode());
        int fallback = 1 + (hash % MAX_OFFLINE_SKILLS);
        for (int i = 0; skills.size() < Math.max(fallback, 1) && i < OFFLINE_LANGUAGES.size(); i++) {
            String language = OFFLINE_LANGUAGES.get(i);
            if (!skills.contains(language)) {
                skills.add(language);
            }
        }
        if (hash % 7 == 0) {
            skills.remove(0);
        }

        List<ImportedEvidence> evidence = new ArrayList<>(skills.size());
        for (int i = 0; i < skills.size(); i++) {
            String skill = skills.get(i);
            double confidence = round(1.0 - (i * 0.2));
            evidence.add(new ImportedEvidence(
                    skill,
                    username + "/" + skill.toLowerCase(Locale.ROOT) + "-samples",
                    "Offline sample: public " + skill + " repositories owned by " + username,
                    confidence));
        }
        return evidence;
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
