package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.SystemConfigManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;

class SemanticMatchHandlerTest {

    private final SemanticMatchHandler handler = new SemanticMatchHandler();

    private static StudentProfile student(Set<String> interests, Set<String> claimed, Set<String> verified) {
        StudentProfile student = new StudentProfile();
        student.setInterests(interests);
        student.setClaimedSkills(claimed);
        student.setVerifiedSkills(verified);
        return student;
    }

    private static Opportunity opportunity(String title, String description) {
        Opportunity opportunity = new Opportunity();
        opportunity.setTitle(title);
        opportunity.setDescription(description);
        return opportunity;
    }

    private MatchContext evaluate(StudentProfile student, Opportunity opportunity) {
        MatchContext context = new MatchContext(student, opportunity);
        MatchLayerHandler.LayerResult result = handler.evaluate(context);
        assertThat(result).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
        return context;
    }

    private Double semanticScore(MatchContext context) {
        return context.getLayerScores().get(SemanticMatchHandler.LAYER);
    }

    private static double weight() {
        return SystemConfigManager.getInstance()
                .getDouble(SystemConfigManager.KEY_SEMANTIC_WEIGHT, SemanticMatchHandler.DEFAULT_SEMANTIC_WEIGHT);
    }

    // ---------------------------------------------------------------- normal matches

    @Test
    void returnsHighSimilarityForOverlappingText() {
        MatchContext context = evaluate(
                student(Set.of("machine learning", "python"), Set.of(), Set.of()),
                opportunity("Machine learning research", "Build Python machine learning models"));

        assertThat(semanticScore(context)).isNotNull().isGreaterThan(0.0);
    }

    @Test
    void overlappingTextScoresHigherThanUnrelated() {
        double overlapping = semanticScore(evaluate(
                student(Set.of("java", "spring"), Set.of(), Set.of()),
                opportunity("Java backend", "Spring boot services in java")));
        double unrelated = semanticScore(evaluate(
                student(Set.of("java", "spring"), Set.of(), Set.of()),
                opportunity("Marine biology", "Coral reef survey field work")));

        assertThat(overlapping).isGreaterThan(unrelated);
    }

    @Test
    void similarityIsWithinUnitInterval() {
        MatchContext context = evaluate(
                student(Set.of("python"), Set.of(), Set.of()),
                opportunity("python", "python"));

        assertThat(semanticScore(context)).isBetween(0.0, weight());
    }

    // ---------------------------------------------------------------- unrelated

    @Test
    void unrelatedTextYieldsZero() {
        MatchContext context = evaluate(
                student(Set.of("java"), Set.of(), Set.of()),
                opportunity("coral reefs", "marine biology fieldwork"));

        assertThat(semanticScore(context)).isEqualTo(0.0);
    }

    // ---------------------------------------------------------------- empty / null

    @Test
    void emptyStudentTextYieldsZero() {
        MatchContext context = evaluate(
                student(Set.of(), Set.of(), Set.of()),
                opportunity("Java", "Backend"));

        assertThat(semanticScore(context)).isEqualTo(0.0);
    }

    @Test
    void emptyOpportunityTextYieldsZero() {
        MatchContext context = evaluate(
                student(Set.of("java"), Set.of(), Set.of()),
                opportunity("", "   "));

        assertThat(semanticScore(context)).isEqualTo(0.0);
    }

    @Test
    void nullCollectionsAndFieldsAreSafe() {
        MatchContext context = evaluate(
                student(null, null, null),
                opportunity(null, null));

        assertThat(semanticScore(context)).isEqualTo(0.0);
    }

    @Test
    void nullEntriesInCollectionsAreIgnored() {
        Set<String> interests = new LinkedHashSet<>();
        interests.add(null);
        interests.add("java");
        interests.add("  ");

        MatchContext context = evaluate(
                student(interests, null, null),
                opportunity("Java", null));

        assertThat(semanticScore(context)).isGreaterThan(0.0);
    }

    @Test
    void repeatedTokensDoNotEscapeUnitInterval() {
        MatchContext context = evaluate(
                student(Set.of("java java java java"), Set.of(), Set.of()),
                opportunity("java", "java java"));

        assertThat(semanticScore(context)).isBetween(0.0, weight());
    }

    @Test
    void identicalDocumentsYieldFullWeight() {
        double contribution = semanticScore(evaluate(
                student(Set.of("java spring"), Set.of(), Set.of()),
                opportunity("java spring", "")));

        assertThat(contribution).isCloseTo(weight(), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void singleSharedTermAmongUniqueTermsStaysBelowFull() {
        double contribution = semanticScore(evaluate(
                student(Set.of("java"), Set.of(), Set.of()),
                opportunity("java coral reefs", "marine biology fieldwork")));

        assertThat(contribution).isGreaterThan(0.0).isLessThan(weight());
    }

    // ---------------------------------------------------------------- weighting

    @Test
    void contributionIsScaledByConfiguredWeight() {
        StudentProfile student = student(Set.of("python", "ml"), Set.of(), Set.of());
        Opportunity opportunity = opportunity("python ml", "python ml models");

        double lowWeight = 0.10;
        double highWeight = 0.80;
        double lowContribution = semanticScoreWithWeight(student, opportunity, lowWeight);
        double highContribution = semanticScoreWithWeight(student, opportunity, highWeight);

        // Same text ⇒ same raw similarity; only the configured weight changes the contribution.
        assertThat(lowContribution).isBetween(0.0, lowWeight);
        assertThat(highContribution).isBetween(0.0, highWeight);
        assertThat(highContribution).isGreaterThan(lowContribution);
        assertThat(highContribution).isCloseTo(
                lowContribution * (highWeight / lowWeight),
                org.assertj.core.data.Offset.offset(1e-9));
    }

    private double semanticScoreWithWeight(StudentProfile student, Opportunity opportunity, double weight) {
        SystemConfigManager mocked = Mockito.mock(SystemConfigManager.class);
        Mockito.when(mocked.getDouble(eq(SystemConfigManager.KEY_SEMANTIC_WEIGHT), anyDouble()))
                .thenReturn(weight);
        SemanticMatchHandler weighted = new SemanticMatchHandler(mocked);
        MatchContext context = new MatchContext(student, opportunity);
        assertThat(weighted.evaluate(context)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
        return context.getLayerScores().get(SemanticMatchHandler.LAYER);
    }

    @Test
    void contributionAccumulatesIntoTotalScore() {
        MatchContext context = evaluate(
                student(Set.of("java"), Set.of(), Set.of()),
                opportunity("java", "java"));

        assertThat(context.getTotalScore()).isEqualTo(semanticScore(context));
    }

    // ---------------------------------------------------------------- structured skills & synonyms

    @Test
    void matchesWhenInterestAppearsOnlyInOpportunitySkills() {
        // The prose never says "machine learning"; only the structured preferred skills do.
        StudentProfile student = student(Set.of("Machine Learning"), Set.of("Python"), Set.of());
        Opportunity opportunity = opportunity(
                "Transformer Models for Cantonese Text",
                "Adapt an open multilingual model to Cantonese.");
        opportunity.setRequiredSkills(Set.of("Python", "PyTorch"));
        opportunity.setPreferredSkills(Set.of("NLP", "Machine Learning"));

        assertThat(semanticScore(evaluate(student, opportunity))).isGreaterThan(0.0);
    }

    @Test
    void matchesCompoundInterestInPreferredSkills() {
        StudentProfile student = student(Set.of("Human-Computer Interaction"), Set.of("Figma"), Set.of());
        Opportunity opportunity = opportunity(
                "Usability Study of Student Portals",
                "Run moderated usability sessions on the student portal.");
        opportunity.setRequiredSkills(Set.of("Figma"));
        opportunity.setPreferredSkills(Set.of("Human-Computer Interaction"));

        assertThat(semanticScore(evaluate(student, opportunity))).isGreaterThan(0.0);
    }

    @Test
    void abbreviationInterestMatchesFullPhraseSkill() {
        double withAlias = semanticScore(evaluate(
                student(Set.of("ml"), Set.of(), Set.of()),
                opportunity("Machine learning research", "Build machine learning models")));
        double unrelated = semanticScore(evaluate(
                student(Set.of("ml"), Set.of(), Set.of()),
                opportunity("Coral reefs", "Marine biology fieldwork")));

        assertThat(withAlias).isGreaterThan(0.0);
        assertThat(withAlias).isGreaterThan(unrelated);
    }
}
