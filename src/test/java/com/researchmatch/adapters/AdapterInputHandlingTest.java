package com.researchmatch.adapters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Adapter input handling: OpenAlex display names must keep their own casing (issue #21) and GitHub
 * handles pasted as profile URLs must be reduced to the bare username (issue #25).
 *
 * <p>Both fixes share a theme: the adapters were rewriting text they had no business rewriting —
 * re-casing a curated display name, and keeping query strings that are part of a URL, not a handle.
 */
class AdapterInputHandlingTest {

    // ================================================================== #21: OpenAlex areas keep their casing

    @Nested
    @DisplayName("#21 OpenAlex display names are preserved verbatim")
    class OpenAlexAreaCasing {

        private List<String> areaFor(String primaryTopicName) {
            OpenAlexPublicationAdapter.Work work = new OpenAlexPublicationAdapter.Work(
                    "w", new OpenAlexPublicationAdapter.Topic(primaryTopicName, null, null), null);
            return OpenAlexPublicationAdapter.deriveAreas("A1", List.of(work)).stream()
                    .map(ImportedEvidence::skillName).toList();
        }

        @ParameterizedTest(name = "\"{0}\" survives unchanged")
        @ValueSource(strings = {
                "NLP",
                "COVID-19",
                "PhD-level analysis",
                "AI",
                "e-Government",
                "iNaturalist taxonomy",
                "3D reconstruction",
                "DNA sequencing",
                "Machine learning"            // already title-cased: still untouched
        })
        void acronymsAndProperNounsAreNotReCased(String name) {
            assertThat(areaFor(name)).containsExactly(name);
        }

        @Test
        void conceptsFallbackAlsoKeepsCasing() {
            OpenAlexPublicationAdapter.Work work = new OpenAlexPublicationAdapter.Work(
                    "w", null, List.of(new OpenAlexPublicationAdapter.Concept("NLP")));

            assertThat(OpenAlexPublicationAdapter.deriveAreas("A1", List.of(work)))
                    .extracting(ImportedEvidence::skillName).containsExactly("NLP");
        }

        @Test
        void areaIsTrimmedButOtherwiseUntouched() {
            assertThat(areaFor("  NLP  ")).containsExactly("NLP");
        }

        @Test
        void inconsistentCasingDropsTheGarbledSpellingInFavourOfTheCuratedOne() {
            // OpenAlex emits the same topic with different casing; carrying "machine learning" through
            // verbatim would otherwise count the garbled spelling as a separate area.
            List<OpenAlexPublicationAdapter.Work> works = List.of(
                    work("Machine learning"),
                    work("machine learning"),
                    work("Machine learning"));

            assertThat(OpenAlexPublicationAdapter.deriveAreas("A1", works))
                    .extracting(ImportedEvidence::skillName).containsExactly("Machine learning");
        }

        private OpenAlexPublicationAdapter.Work work(String name) {
            return new OpenAlexPublicationAdapter.Work(
                    "w", new OpenAlexPublicationAdapter.Topic(name, null, null), null);
        }
    }

    // ================================================================== #25: GitHub handle parsing

    @Nested
    @DisplayName("#25 pasted profile URLs reduce to the bare handle")
    class GitHubHandleParsing {

    /** A trailing slash used to be stripped by the caller; normaliseHandle now owns that too. */
    private String handleOf(String input) {
        // The parsing is package-private so tests can pin it directly rather than through a whole import.
        return GitHubSkillAdapter.normaliseHandle(input);
    }

        @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
        @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
                "octocat                                  | octocat",
                "  octocat  | octocat",
                "https://github.com/octocat               | octocat",
                "https://github.com/octocat/              | octocat",
                "http://github.com/octocat                | octocat",
                "github.com/octocat                       | octocat",
                "www.github.com/octocat                   | octocat",
                "https://www.github.com/octocat/          | octocat",
                "https://github.com/octocat?tab=repositories | octocat",
                "https://github.com/octocat/?tab=repositories | octocat",
                "https://github.com/octocat#readme        | octocat",
                "https://github.com/octocat/repos         | octocat",
                "https://github.com/octocat/repositories  | octocat",
                "  https://github.com/octocat/  | octocat",
                "HTTPS://GitHub.com/OctoCat               | OctoCat"
        })
        void urlFormsReduceToTheUsername(String input, String expected) {
            assertThat(handleOf(input)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "\"{0}\" is rejected")
        @ValueSource(strings = {
                "https://github.com/",
                "https://github.com",
                "https://github.com/?tab=repositories",
                "https://github.com/#readme"
        })
        void aUrlWithNoUsernameIsRejected(String input) {
            assertThatThrownBy(() -> handleOf(input))
                    .isInstanceOf(com.researchmatch.config.InvalidRequestException.class)
                    .hasMessageContaining("handle");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "\t\n"})
        void nullOrBlankIsRejected(String input) {
            assertThatThrownBy(() -> handleOf(input))
                    .isInstanceOf(com.researchmatch.config.InvalidRequestException.class);
        }

        @Test
        void theParsedHandleReachesTheLiveApiCleanly() {
            // The bug was visible here: the query string used to ride along into the request path.
            MockRestServiceServer[] holder = new MockRestServiceServer[1];
            RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
            holder[0] = MockRestServiceServer.bindTo(builder).build();
            GitHubSkillAdapter adapter = new GitHubSkillAdapter("ghp_x", builder.build());

            holder[0].expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("[{\"name\":\"a\",\"language\":\"Java\",\"topics\":[]}]",
                            MediaType.APPLICATION_JSON));

            List<ImportedEvidence> evidence =
                    adapter.importEvidence("https://github.com/octocat?tab=repositories");

            holder[0].verify();
            assertThat(evidence).extracting(ImportedEvidence::skillName).containsExactly("Java");
        }
    }
}
