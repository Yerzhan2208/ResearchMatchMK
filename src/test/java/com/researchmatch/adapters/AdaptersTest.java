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
        assertThat(new GitHubSkillAdapter(null, null).platformName()).isEqualTo(GitHubSkillAdapter.PLATFORM);
        assertThat(new OpenAlexPublicationAdapter(false, null, null).platformName())
                .isEqualTo(OpenAlexPublicationAdapter.PLATFORM);
    }

    @Test
    void adaptersImportWithoutNetworkAndRejectBlankHandles() {
        List<ImportedEvidence> github = new GitHubSkillAdapter("  ", null).importEvidence("octocat");
        assertThat(github).isNotEmpty();

        OpenAlexPublicationAdapter openAlex = new OpenAlexPublicationAdapter(false, null, null);
        assertThatThrownBy(() -> openAlex.importEvidence("  "))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void registryResolvesAdaptersCaseInsensitively() {
        ResearchDataSourceRegistry registry = new AdapterRegistry(
                List.of(new GitHubSkillAdapter(null, null), new OpenAlexPublicationAdapter(false, null, null)));

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
        List<ResearchDataAdapter> mutable = new java.util.ArrayList<>(List.of(new GitHubSkillAdapter(null, null)));
        AdapterRegistry registry = new AdapterRegistry(mutable);
        mutable.clear();

        assertThat(registry.sources()).hasSize(1);
    }
}
