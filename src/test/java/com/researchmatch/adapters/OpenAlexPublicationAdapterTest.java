package com.researchmatch.adapters;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResearchMatchException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAlexPublicationAdapterTest {

    private static RestClient client(MockRestServiceServer[] holder) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openalex.org");
        holder[0] = MockRestServiceServer.bindTo(builder).build();
        return builder.build();
    }

    // ================================================================== offline mode

    @Nested
    class OfflineMode {

        private final OpenAlexPublicationAdapter adapter = new OpenAlexPublicationAdapter(false, null, null);

        @Test
        void isOfflineWhenNotEnabled() {
            assertThat(adapter.isOfflineMode()).isTrue();
            assertThat(new OpenAlexPublicationAdapter(true, null, null).isOfflineMode()).isFalse();
        }

        @Test
        void isDeterministicForTheSameAuthorId() {
            assertThat(adapter.importEvidence("A5012345601"))
                    .isEqualTo(adapter.importEvidence("A5012345601"));
        }

        @Test
        void neverReturnsNullOrNullElementsAndKeepsAreasDistinct() {
            for (String id : List.of("A1", "A1234567890", "A999", "A77777")) {
                List<ImportedEvidence> evidence = adapter.importEvidence(id);
                assertThat(evidence).isNotNull().doesNotContainNull();
                assertThat(evidence).extracting(ImportedEvidence::skillName).doesNotHaveDuplicates();
            }
        }

        @Test
        void reportsAtMostTheCapAndInBoundsConfidences() {
            for (String id : List.of("A1", "A2", "A3", "A4", "A5", "A6")) {
                List<ImportedEvidence> evidence = adapter.importEvidence(id);
                assertThat(evidence).hasSizeLessThanOrEqualTo(OpenAlexPublicationAdapter.MAX_OFFLINE_AREAS);
                assertThat(evidence).allSatisfy(
                        e -> assertThat(e.confidence()).isBetween(0.0, 1.0));
            }
        }

        @Test
        void evidenceIsAttributedToTheAuthorId() {
            assertThat(adapter.importEvidence("A5012345601"))
                    .allSatisfy(e -> assertThat(e.sourceName()).isEqualTo("openalex:A5012345601"));
        }

        @Test
        void someAuthorIdsModelAnAuthorWithNoIndexedWorks() {
            List<String> emptyIds = new java.util.ArrayList<>();
            for (int i = 0; i < 200; i++) {
                String id = "A" + i;
                if (adapter.importEvidence(id).isEmpty()) {
                    emptyIds.add(id);
                }
            }

            // The offline profile deliberately models "author with no indexed works" for some ids,
            // so the empty result path is exercised without inventing data.
            assertThat(emptyIds).isNotEmpty();
            assertThat(adapter.importEvidence(emptyIds.get(0))).isEmpty();
        }

        @Test
        void rejectsBlankAndMalformedAuthorIds() {
            assertThatThrownBy(() -> adapter.importEvidence(null)).isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> adapter.importEvidence("   ")).isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> adapter.importEvidence("12345")).isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("A1234567890");
            assertThatThrownBy(() -> adapter.importEvidence("A")).isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void stripsAFullOpenAlexUrl() {
            assertThat(adapter.importEvidence("https://openalex.org/A5012345601"))
                    .isEqualTo(adapter.importEvidence("A5012345601"));
        }
    }

    // ================================================================== payload parsing

    @Nested
    class Parsing {

        private static OpenAlexPublicationAdapter.Work workWith(String displayed, String subfield,
                                                               String field, String... concepts) {
            List<OpenAlexPublicationAdapter.Concept> conceptList = Arrays.stream(concepts)
                    .map(name -> new OpenAlexPublicationAdapter.Concept(name))
                    .toList();
            return new OpenAlexPublicationAdapter.Work(
                    "title", new OpenAlexPublicationAdapter.Topic(displayed, subfield, field), conceptList);
        }

        @Test
        void prefersThePrimaryTopicDisplayName() {
            List<ImportedEvidence> evidence = OpenAlexPublicationAdapter.deriveAreas("A1",
                    List.of(workWith("Natural language processing", "AI", "CS", "Machine learning")));

            assertThat(evidence).extracting(ImportedEvidence::skillName)
                    .containsExactly("Natural language processing");
        }

        @Test
        void fallsBackToSubfieldThenField() {
            assertThat(OpenAlexPublicationAdapter.deriveAreas("A1", List.of(workWith(" ", "Data mining", "CS"))))
                    .extracting(ImportedEvidence::skillName).containsExactly("Data mining");
            assertThat(OpenAlexPublicationAdapter.deriveAreas("A1", List.of(workWith(null, null, "Vision"))))
                    .extracting(ImportedEvidence::skillName).containsExactly("Vision");
        }

        @Test
        void fallsBackToTheFirstConceptWhenThereIsNoPrimaryTopic() {
            OpenAlexPublicationAdapter.Work work = new OpenAlexPublicationAdapter.Work(
                    "t", null, List.of(new OpenAlexPublicationAdapter.Concept("Computer vision")));

            assertThat(OpenAlexPublicationAdapter.deriveAreas("A1", List.of(work)))
                    .extracting(ImportedEvidence::skillName).containsExactly("Computer vision");
        }

        @Test
        void countsRepeatedAreasTogetherAndNormalisesCase() {
            List<OpenAlexPublicationAdapter.Work> works = List.of(
                    workWith("Data mining", null, null),
                    workWith("data mining", null, null),
                    workWith("Computer vision", null, null));

            List<ImportedEvidence> evidence = OpenAlexPublicationAdapter.deriveAreas("A1", works);

            assertThat(evidence).hasSize(2);
            ImportedEvidence mining = evidence.get(0);
            assertThat(mining.skillName()).isEqualTo("Data mining");
            assertThat(mining.confidence()).isEqualTo(0.67); // 2 of 3 works
        }

        @Test
        void titleCasesLowercaseOpenAlexTags() {
            List<ImportedEvidence> evidence = OpenAlexPublicationAdapter.deriveAreas("A1",
                    List.of(workWith("natural language processing", null, null)));

            assertThat(evidence.get(0).skillName()).isEqualTo("Natural language processing");
        }

        @Test
        void skipsWorksWithNoUsableAreaAndNullEntries() {
            List<OpenAlexPublicationAdapter.Work> works = Arrays.asList(
                    null,
                    new OpenAlexPublicationAdapter.Work("t", null, null),
                    new OpenAlexPublicationAdapter.Work("t", new OpenAlexPublicationAdapter.Topic(" ", " ", " "),
                            List.of(new OpenAlexPublicationAdapter.Concept(" "))));

            assertThat(OpenAlexPublicationAdapter.deriveAreas("A1", works)).isEmpty();
        }

        @Test
        void returnsEmptyForNoWorks() {
            assertThat(OpenAlexPublicationAdapter.deriveAreas("A1", List.of())).isEmpty();
        }

        @Test
        void aSingleWorkGivesFullConfidence() {
            List<ImportedEvidence> evidence = OpenAlexPublicationAdapter.deriveAreas("A1",
                    List.of(workWith("Vision", null, null)));

            assertThat(evidence.get(0).confidence()).isEqualTo(1.0);
            assertThat(evidence.get(0).evidenceSnippet()).contains("1 of 1");
        }
    }

    // ================================================================== online mode

    @Nested
    class OnlineMode {

        @Test
        void callsTheWorksEndpointAndParsesTheJsonPayload() {
            MockRestServiceServer[] server = new MockRestServiceServer[1];
            OpenAlexPublicationAdapter adapter = new OpenAlexPublicationAdapter(true, "", client(server));
            server[0].expect(requestTo(
                            "https://api.openalex.org/works?filter=author.id:A5012345601&per-page=50"))
                    .andRespond(withSuccess("""
                            {"results":[
                              {"title":"NLP study","primary_topic":{"display_name":"natural language processing",
                               "subfield":"Artificial Intelligence","field":"Computer Science"},"concepts":[]},
                              {"title":"NLP again","primary_topic":{"display_name":"natural language processing",
                               "subfield":"Artificial Intelligence","field":"Computer Science"},"concepts":[]},
                              {"title":"Vision","primary_topic":{"display_name":"computer vision",
                               "subfield":"Computer Vision","field":"Computer Science"},"concepts":[]}
                            ]}
                            """, MediaType.APPLICATION_JSON));

            List<ImportedEvidence> evidence = adapter.importEvidence("A5012345601");

            server[0].verify();
            assertThat(evidence).extracting(ImportedEvidence::skillName)
                    .containsExactly("Natural language processing", "Computer vision");
            assertThat(evidence.get(0).confidence()).isEqualTo(0.67);
        }

        @Test
        void includesThePolitePoolMailtoWhenConfigured() {
            MockRestServiceServer[] server = new MockRestServiceServer[1];
            OpenAlexPublicationAdapter adapter = new OpenAlexPublicationAdapter(
                    true, "team@cityu.edu.hk", client(server));
            server[0].expect(requestTo(
                            "https://api.openalex.org/works?filter=author.id:A1&per-page=50"
                                    + "&mailto=team@cityu.edu.hk"))
                    .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

            assertThat(adapter.importEvidence("A1")).isEmpty();
            server[0].verify();
        }

        @Test
        void aNullResultsBlockYieldsNoEvidence() {
            MockRestServiceServer[] server = new MockRestServiceServer[1];
            OpenAlexPublicationAdapter adapter = new OpenAlexPublicationAdapter(true, null, client(server));
            server[0].expect(requestTo("https://api.openalex.org/works?filter=author.id:A1&per-page=50"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertThat(adapter.importEvidence("A1")).isEmpty();
        }

        @Test
        void aServerErrorIsSurfacedAsAResearchMatchException() {
            MockRestServiceServer[] server = new MockRestServiceServer[1];
            OpenAlexPublicationAdapter adapter = new OpenAlexPublicationAdapter(true, null, client(server));
            server[0].expect(requestTo("https://api.openalex.org/works?filter=author.id:A1&per-page=50"))
                    .andRespond(withServerError());

            assertThatThrownBy(() -> adapter.importEvidence("A1"))
                    .isInstanceOf(ResearchMatchException.class)
                    .hasMessageContaining("A1");
        }
    }
}
