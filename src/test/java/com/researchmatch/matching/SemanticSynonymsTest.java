package com.researchmatch.matching;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SemanticSynonyms}: alias expansion and compound-token splitting used by the semantic
 * layer to reward abbreviation-vs-phrase overlap (e.g. a student's "ML" interest matching "machine learning").
 */
class SemanticSynonymsTest {

    @Test
    void expandsKnownAbbreviationsToCanonicalPhrase() {
        assertThat(SemanticSynonyms.expand("ml")).containsExactly("machine", "learning");
        assertThat(SemanticSynonyms.expand("NLP")).containsExactly("natural", "language", "processing");
        assertThat(SemanticSynonyms.expand("hci")).containsExactly("human", "computer", "interaction");
        assertThat(SemanticSynonyms.expand("cv")).containsExactly("computer", "vision");
    }

    @Test
    void passesUnknownTokensThroughLowercased() {
        assertThat(SemanticSynonyms.expand("Java-Spring_Boot!!")).containsExactly("java", "spring", "boot");
    }

    @Test
    void splitsHyphenatedCompoundInterests() {
        assertThat(SemanticSynonyms.expand("Human-Computer"))
                .containsExactly("human", "computer");
    }

    @Test
    void mixesAliasesAndPlainTokensInOrder() {
        assertThat(SemanticSynonyms.expand("python ml research"))
                .containsExactly("python", "machine", "learning", "research");
    }

    @Test
    void nullBlankAndSeparatorsYieldEmpty() {
        assertThat(SemanticSynonyms.expand(null)).isEmpty();
        assertThat(SemanticSynonyms.expand("")).isEmpty();
        assertThat(SemanticSynonyms.expand("   ")).isEmpty();
        assertThat(SemanticSynonyms.expand("!!!---")).isEmpty();
    }
}
