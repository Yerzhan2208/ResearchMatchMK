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

            assertThat(first).isNotEmpty().isEqualTo(second);
        }

        @Test
        void neverReturnsNullOrNullElements() {
            for (String handle : List.of("octocat", "a", "some-long-handle-123", "demo-mei-ling-ho")) {
                List<ImportedEvidence> evidence = adapter.importEvidence(handle);
                assertThat(evidence).isNotNull().doesNotContainNull();
            }
        }

        @Test
        void returnsEmptyForHandlesWithoutACuratedFixture() {
            // The whole point of option B: an unverified handle gets nothing, never invented skills.
            assertThat(adapter.importEvidence("definitely-not-a-real-user-xyz-9f3k")).isEmpty();
            assertThat(adapter.importEvidence("this-account-does-not-exist-7h2p")).isEmpty();
            assertThat(adapter.importEvidence("demo-mei-ling-ho-typo")).isEmpty();
        }

        @Test
        void theHandleSpellingNeverCreatesASkill() {
            // "demo-mei-ling-ho" has a fixture, but its name is irrelevant; the fixture is, not the spelling.
            assertThat(adapter.importEvidence("demo-mei-ling-ho"))
                    .extracting(ImportedEvidence::skillName)
                    .containsExactly("Python", "Jupyter Notebook");
            assertThat(adapter.importEvidence("javascript-fan")).isEmpty();
        }

        @Test
        void servesTheFixtureForALowercaseOrUrlHandle() {
            List<String> canonical = adapter.importEvidence("octocat")
                    .stream().map(ImportedEvidence::skillName).toList();

            assertThat(adapter.importEvidence("OctoCat"))
                    .extracting(ImportedEvidence::skillName).containsExactlyElementsOf(canonical);
            assertThat(adapter.importEvidence("  https://github.com/octocat/  "))
                    .extracting(ImportedEvidence::skillName).containsExactlyElementsOf(canonical);
        }

        @Test
        void fixtureEvidenceIsLabelledAsSampleDataNotAsFetchedEvidence() {
            List<ImportedEvidence> evidence = adapter.importEvidence("octocat");

            assertThat(evidence).isNotEmpty();
            assertThat(evidence).allSatisfy(e -> {
                assertThat(e.sourceName()).startsWith(ResearchDataAdapter.FIXTURE_SOURCE_PREFIX);
                assertThat(e.evidenceSnippet()).startsWith(ResearchDataAdapter.OFFLINE_SNIPPET_PREFIX);
                assertThat(e.isSampleData()).isTrue();
                // The old fabrication claimed real repositories for any handle; that phrasing is gone.
                assertThat(e.evidenceSnippet()).doesNotContain("public").doesNotContain("owned by");
            });
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
        void reportsNoMoreThanTheCap() {
            for (String handle : List.of("octocat", "demo-mei-ling-ho", "demo-arjun-patel", "demo-sze-wing-lau",
                    "zzz")) {
                assertThat(adapter.importEvidence(handle)).hasSizeLessThanOrEqualTo(
                        GitHubSkillAdapter.MAX_OFFLINE_SKILLS);
            }
        }

        @Test
        void everyCuratedFixtureRespectsTheCapAndPointsAtItsOwnHandle() {
            assertThat(GitHubSkillAdapter.OFFLINE_FIXTURES).isNotEmpty();
            GitHubSkillAdapter.OFFLINE_FIXTURES.forEach((handle, samples) -> {
                assertThat(samples).isNotEmpty().hasSizeLessThanOrEqualTo(GitHubSkillAdapter.MAX_OFFLINE_SKILLS);
                assertThat(samples).allSatisfy(e -> assertThat(e.sourceName()).startsWith(handle + "/"));
                assertThat(adapter.importEvidence(handle))
                        .extracting(ImportedEvidence::skillName)
                        .containsExactlyElementsOf(samples.stream().map(ImportedEvidence::skillName).toList());
            });
        }

        @Test
        void everySeededStudentHandleHitsALiveFixture() {
            // Guards the seeder/fixture agreement: a student whose handle has no fixture would see
            // "No skills were found" for their own profile on a fresh checkout.
            for (String handle : List.of("demo-mei-ling-ho", "demo-arjun-patel", "demo-sze-wing-lau")) {
                assertThat(adapter.importEvidence(handle))
                        .as("fixture for seeded student %s", handle)
                        .isNotEmpty();
            }
        }

        @Test
        void rejectsBlankHandles() {
            assertThatThrownBy(() -> adapter.importEvidence(null)).isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> adapter.importEvidence("  ")).isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> adapter.importEvidence("https://github.com/"))
                    .isInstanceOf(InvalidRequestException.class);
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
