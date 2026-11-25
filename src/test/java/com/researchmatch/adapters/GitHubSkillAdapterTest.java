package com.researchmatch.adapters;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResearchMatchException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubSkillAdapterTest {

    private static RestClient client(MockRestServiceServer[] holder) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        holder[0] = MockRestServiceServer.bindTo(builder).build();
        return builder.build();
    }

    // ================================================================== offline mode

    @Nested
    class OfflineMode {

        private final GitHubSkillAdapter adapter = new GitHubSkillAdapter("", null);

        @Test
        void isOfflineWhenNoTokenIsConfigured() {
            assertThat(adapter.isOfflineMode()).isTrue();
            assertThat(new GitHubSkillAdapter(null, null).isOfflineMode()).isTrue();
            assertThat(new GitHubSkillAdapter("  ", null).isOfflineMode()).isTrue();
        }

        @Test
        void isOnlineWhenATokenIsConfigured() {
            assertThat(new GitHubSkillAdapter("ghp_token", null).isOfflineMode()).isFalse();
        }

        @Test
        void isDeterministicForTheSameHandle() {
            List<ImportedEvidence> first = adapter.importEvidence("octocat");
            List<ImportedEvidence> second = adapter.importEvidence("octocat");

            assertThat(first).isEqualTo(second);
        }

        @Test
        void neverReturnsNullOrNullElements() {
            for (String handle : List.of("octocat", "a", "some-long-handle-123", "java-wizard")) {
                List<ImportedEvidence> evidence = adapter.importEvidence(handle);
                assertThat(evidence).isNotNull().doesNotContainNull();
            }
        }

        @Test
        void reportsTheLanguageNamedInTheHandle() {
            assertThat(adapter.importEvidence("java-wizard"))
                    .extracting(ImportedEvidence::skillName)
                    .contains("Java");
            assertThat(adapter.importEvidence("pythonista"))
                    .extracting(ImportedEvidence::skillName)
                    .contains("Python");
        }

        @Test
        void confidenceStaysWithinBoundsAndDecreases() {
            List<ImportedEvidence> evidence = adapter.importEvidence("octocat");

            assertThat(evidence).allSatisfy(e -> assertThat(e.confidence()).isBetween(0.0, 1.0));
            for (int i = 1; i < evidence.size(); i++) {
                assertThat(evidence.get(i).confidence())
                        .isLessThanOrEqualTo(evidence.get(i - 1).confidence());
            }
        }

        @Test
        void evidenceIsAttributedToTheHandle() {
            assertThat(adapter.importEvidence("octocat"))
                    .allSatisfy(e -> {
                        assertThat(e.sourceName()).contains("octocat");
                        assertThat(e.evidenceSnippet()).contains("octocat");
                    });
        }

        @Test
        void someHandlesYieldFewerSkillsButNeverMoreThanTheCap() {
            assertThat(adapter.importEvidence("octocat")).hasSizeLessThanOrEqualTo(3);
            assertThat(adapter.importEvidence("zzz")).hasSizeLessThanOrEqualTo(3);
        }

        @Test
        void rejectsBlankHandles() {
            assertThatThrownBy(() -> adapter.importEvidence(null)).isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> adapter.importEvidence("  ")).isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void acceptsAProfileUrlAndTrimsWhitespace() {
            assertThat(adapter.importEvidence("  https://github.com/octocat/  "))
                    .anySatisfy(e -> assertThat(e.sourceName()).contains("octocat"));
        }
    }

    // ================================================================== payload parsing

    @Nested
    class Parsing {

        @Test
        void derivesOneEntryPerLanguageAndTopicRankedByFrequency() {
            List<GitHubSkillAdapter.Repo> repos = List.of(
                    new GitHubSkillAdapter.Repo("a", "Java", List.of("spring-boot")),
                    new GitHubSkillAdapter.Repo("b", "Java", List.of()),
                    new GitHubSkillAdapter.Repo("c", "Python", List.of("spring-boot")));

            List<ImportedEvidence> evidence = GitHubSkillAdapter.deriveEvidence("octocat", repos);

            ImportedEvidence java = evidence.stream()
                    .filter(e -> e.skillName().equals("Java")).findFirst().orElseThrow();
            ImportedEvidence python = evidence.stream()
                    .filter(e -> e.skillName().equals("Python")).findFirst().orElseThrow();
            ImportedEvidence spring = evidence.stream()
                    .filter(e -> e.skillName().equals("Spring-boot")).findFirst().orElseThrow();

            assertThat(java.confidence()).isEqualTo(0.67);   // 2 of 3 repos
            assertThat(python.confidence()).isEqualTo(0.33); // 1 of 3 repos
            assertThat(spring.confidence()).isEqualTo(0.67);
            assertThat(java.sourceName()).isEqualTo("a");
        }

        @Test
        void skipsNullReposBlankLanguagesAndBlankTopics() {
            List<GitHubSkillAdapter.Repo> repos = Arrays.asList(
                    null,
                    new GitHubSkillAdapter.Repo("a", null, null),
                    new GitHubSkillAdapter.Repo("b", "  ", List.of("", "  ")));

            assertThat(GitHubSkillAdapter.deriveEvidence("octocat", repos)).isEmpty();
        }

        @Test
        void returnsEmptyForNoRepositories() {
            assertThat(GitHubSkillAdapter.deriveEvidence("octocat", List.of())).isEmpty();
            assertThat(GitHubSkillAdapter.deriveEvidence("octocat", new ArrayList<>())).isEmpty();
        }

        @Test
        void confidenceForEveryRepositoryIsOne() {
            List<ImportedEvidence> evidence = GitHubSkillAdapter.deriveEvidence(
                    "octocat", List.of(new GitHubSkillAdapter.Repo("a", "Rust", List.of())));

            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).confidence()).isEqualTo(1.0);
        }
    }

    // ================================================================== online mode

    @Nested
    class OnlineMode {

        @Test
        void callsTheReposEndpointAndParsesTheJson() {
            MockRestServiceServer[] server = new MockRestServiceServer[1];
            GitHubSkillAdapter adapter = new GitHubSkillAdapter("ghp_x", client(server));
            server[0].expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                            [{"name":"hello","language":"Java","topics":["cli"]},
                             {"name":"world","language":"Python","topics":[]}]
                            """, MediaType.APPLICATION_JSON));

            List<ImportedEvidence> evidence = adapter.importEvidence("octocat");

            server[0].verify();
            assertThat(evidence).extracting(ImportedEvidence::skillName)
                    .containsExactlyInAnyOrder("Java", "Python", "Cli");
        }

        @Test
        void emptyRepositoryListYieldsNoEvidence() {
            MockRestServiceServer[] server = new MockRestServiceServer[1];
            GitHubSkillAdapter adapter = new GitHubSkillAdapter("ghp_x", client(server));
            server[0].expect(requestTo("https://api.github.com/users/ghost/repos?per_page=100"))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            assertThat(adapter.importEvidence("ghost")).isEmpty();
        }

        @Test
        void aServerErrorIsSurfacedAsAResearchMatchException() {
            MockRestServiceServer[] server = new MockRestServiceServer[1];
            GitHubSkillAdapter adapter = new GitHubSkillAdapter("ghp_x", client(server));
            server[0].expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100"))
                    .andRespond(withServerError());

            assertThatThrownBy(() -> adapter.importEvidence("octocat"))
                    .isInstanceOf(ResearchMatchException.class)
                    .hasMessageContaining("octocat");
        }
    }
}
