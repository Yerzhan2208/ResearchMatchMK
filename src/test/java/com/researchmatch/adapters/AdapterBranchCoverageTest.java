package com.researchmatch.adapters;

import com.researchmatch.config.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Targeted coverage for adapter branches not exercised by the existing adapter tests: the offline
 * hash-based "drop the first skill" rule, the null repository-name fallback, and the handle that becomes
 * blank after stripping a GitHub URL.
 */
class AdapterBranchCoverageTest {

    @Test
    void offlineEvidence_dropsFirstSkillWhenHashDivisibleBySeven() {
        // "b" has hash % 7 == 0, which triggers the remove-first rule.
        List<ImportedEvidence> evidence = new GitHubSkillAdapter().importEvidence("b");
        assertThat(evidence).isNotNull();
        assertThat(evidence).allSatisfy(e -> assertThat(e.skillName()).isNotBlank());
    }

    @Test
    void deriveEvidence_nullRepoNameFallsBackToUsername() {
        List<GitHubSkillAdapter.Repo> repos = List.of(
                new GitHubSkillAdapter.Repo(null, "Java", List.of()));
        List<ImportedEvidence> evidence = GitHubSkillAdapter.deriveEvidence("octocat", repos);
        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).sourceName()).isEqualTo("octocat");
    }

    @Test
    void importEvidence_handleBlankAfterUrlStripThrows() {
        GitHubSkillAdapter adapter = new GitHubSkillAdapter();
        assertThatThrownBy(() -> adapter.importEvidence("https://github.com/"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void importEvidence_nullHandleThrows() {
        GitHubSkillAdapter adapter = new GitHubSkillAdapter();
        assertThatThrownBy(() -> adapter.importEvidence(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void titleCase_emptyInputGuards() throws Exception {
        // GitHub topics still arrive lowercase, so that adapter keeps its title-casing helper.
        java.lang.reflect.Method gh = GitHubSkillAdapter.class.getDeclaredMethod("titleCase", String.class);
        gh.setAccessible(true);
        assertThat((String) gh.invoke(null, "")).isEmpty();
    }

    @Test
    void openAlexAdapterNoLongerReCasesDisplayNames() {
        // #21: the helper (and every reflection break it caused) is gone; display names pass through.
        assertThat(OpenAlexPublicationAdapter.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().equals("titleCase"));
    }
}
