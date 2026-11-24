package com.researchmatch.adapters;

import com.researchmatch.config.AdapterRegistry;
import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResearchDataSourceRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptersTest {

    @Test
    void importedEvidenceValidatesItsInput() {
        ImportedEvidence evidence = new ImportedEvidence("Java", "repo", "class Main {}", 0.9);

        assertThat(evidence.skillName()).isEqualTo("Java");
        assertThat(evidence.confidence()).isEqualTo(0.9);
        assertThatThrownBy(() -> new ImportedEvidence(" ", "r", "s", 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportedEvidence(null, "r", "s", 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportedEvidence("Java", "r", "s", 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportedEvidence("Java", "r", "s", -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adaptersExposeTheirPlatformName() {
        assertThat(new GitHubSkillAdapter().platformName()).isEqualTo(GitHubSkillAdapter.PLATFORM);
        assertThat(new OpenAlexPublicationAdapter().platformName()).isEqualTo(OpenAlexPublicationAdapter.PLATFORM);
    }

    @Test
    void adapterImportsAreExplicitTodos() {
        assertThatThrownBy(() -> new GitHubSkillAdapter().importEvidence("octocat"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("GitHubSkillAdapter");
        assertThatThrownBy(() -> new OpenAlexPublicationAdapter().importEvidence("A123"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("OpenAlexPublicationAdapter");
    }

    @Test
    void registryResolvesAdaptersCaseInsensitively() {
        ResearchDataSourceRegistry registry = new AdapterRegistry(
                List.of(new GitHubSkillAdapter(), new OpenAlexPublicationAdapter()));

        assertThat(registry.forSource("github")).get().isInstanceOf(GitHubSkillAdapter.class);
        assertThat(registry.forSource("  OpenAlex ")).get().isInstanceOf(OpenAlexPublicationAdapter.class);
        assertThat(registry.forSource("bitbucket")).isEmpty();
        assertThat(registry.sources()).hasSize(2);
    }

    @Test
    void registryRejectsBlankSource() {
        ResearchDataSourceRegistry registry = new AdapterRegistry(List.of());

        assertThatThrownBy(() -> registry.forSource(null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> registry.forSource("  ")).isInstanceOf(InvalidRequestException.class);
        assertThat(registry.forSource("github")).isEqualTo(Optional.empty());
    }

    @Test
    void registrySnapshotsItsSources() {
        List<ResearchDataAdapter> mutable = new java.util.ArrayList<>(List.of(new GitHubSkillAdapter()));
        AdapterRegistry registry = new AdapterRegistry(mutable);
        mutable.clear();

        assertThat(registry.sources()).hasSize(1);
    }
}
