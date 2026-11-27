package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceScoreHandlerTest {

    private final EvidenceScoreHandler handler = new EvidenceScoreHandler();

    private static StudentProfile student(Set<String> claimed, Set<String> verified) {
        StudentProfile student = new StudentProfile();
        student.setClaimedSkills(claimed);
        student.setVerifiedSkills(verified);
        return student;
    }

    private static Opportunity opportunity(Set<String> required, Set<String> preferred) {
        Opportunity opportunity = new Opportunity();
        opportunity.setRequiredSkills(required);
        opportunity.setPreferredSkills(preferred);
        return opportunity;
    }

    private MatchContext evaluate(StudentProfile student, Opportunity opportunity) {
        MatchContext context = new MatchContext(student, opportunity);
        MatchLayerHandler.LayerResult result = handler.evaluate(context);
        assertThat(result).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
        return context;
    }

    private Double score(MatchContext context) {
        return context.getLayerScores().get(EvidenceScoreHandler.LAYER);
    }

    /** Expected contribution for {@code verifiedMatches} verified skills: (raw/MAX_BONUS) * evidenceWeight. */
    private static double expected(int verifiedMatches) {
        double raw = Math.min(verifiedMatches * EvidenceScoreHandler.BONUS_PER_VERIFIED_SKILL,
                EvidenceScoreHandler.MAX_BONUS);
        return (raw / EvidenceScoreHandler.MAX_BONUS) * ScoringWeights.DEFAULT_EVIDENCE_WEIGHT;
    }

    // ---------------------------------------------------------------- normal

    @Test
    void verifiedMatchesCountAsMatchedAndEarnBonus() {
        MatchContext context = evaluate(
                student(Set.of(), Set.of("Java")),
                opportunity(Set.of("Java"), Set.of()));

        assertThat(context.getMatchedSkills()).containsExactly("java");
        assertThat(context.getMissingSkills()).isEmpty();
        assertThat(score(context)).isCloseTo(expected(1), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void claimedOnlyMatchCountsAsMatchedButEarnsNoBonus() {
        MatchContext context = evaluate(
                student(Set.of("Java"), Set.of()),
                opportunity(Set.of("Java"), Set.of()));

        assertThat(context.getMatchedSkills()).containsExactly("java");
        assertThat(score(context)).isEqualTo(0.0);
    }

    @Test
    void absentRequiredSkillIsMissing() {
        MatchContext context = evaluate(
                student(Set.of(), Set.of("Python")),
                opportunity(Set.of("Java"), Set.of()));

        assertThat(context.getMatchedSkills()).isEmpty();
        assertThat(context.getMissingSkills()).containsExactly("java");
        assertThat(score(context)).isEqualTo(0.0);
    }

    @Test
    void preferredSkillsMatchedButNeverMissing() {
        MatchContext context = evaluate(
                student(Set.of(), Set.of("Kotlin")),
                opportunity(Set.of(), Set.of("Kotlin", "Scala")));

        assertThat(context.getMatchedSkills()).containsExactly("kotlin");
        assertThat(context.getMissingSkills()).isEmpty();
        assertThat(score(context)).isCloseTo(expected(1), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void mixedSkillsAccumulateBonusAcrossVerifiedMatches() {
        MatchContext context = evaluate(
                student(Set.of("SQL"), Set.of("Java", "Python")),
                opportunity(Set.of("Java", "Python", "SQL", "Go"), Set.of()));

        assertThat(context.getMatchedSkills()).containsExactlyInAnyOrder("java", "python", "sql");
        assertThat(context.getMissingSkills()).containsExactly("go");
        // Java + Python verified ⇒ 2 * bonus; SQL is claimed only ⇒ no extra.
        assertThat(score(context)).isCloseTo(expected(2), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void bonusIsCapped() {
        Set<String> verified = Set.of("a", "b", "c", "d", "e", "f", "g");
        MatchContext context = evaluate(
                student(Set.of(), verified),
                opportunity(verified, Set.of()));

        assertThat(score(context)).isCloseTo(expected(7), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void caseAndWhitespaceAreIgnored() {
        MatchContext context = evaluate(
                student(Set.of(), Set.of("  jAvA ")),
                opportunity(Set.of("JAVA"), Set.of()));

        assertThat(context.getMatchedSkills()).containsExactly("java");
        assertThat(score(context)).isCloseTo(expected(1), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void scoreAccumulatesIntoTotalScore() {
        MatchContext context = evaluate(
                student(Set.of(), Set.of("Java")),
                opportunity(Set.of("Java"), Set.of()));

        assertThat(context.getTotalScore()).isEqualTo(score(context));
    }

    // ---------------------------------------------------------------- edge cases

    @Test
    void noRequiredOrPreferredSkillsYieldsZero() {
        MatchContext context = evaluate(
                student(Set.of("Java"), Set.of("Python")),
                opportunity(Set.of(), Set.of()));

        assertThat(context.getMatchedSkills()).isEmpty();
        assertThat(context.getMissingSkills()).isEmpty();
        assertThat(score(context)).isEqualTo(0.0);
    }

    @Test
    void nullCollectionsAreSafe() {
        MatchContext context = evaluate(
                student(null, null),
                opportunity(null, null));

        assertThat(context.getMatchedSkills()).isEmpty();
        assertThat(context.getMissingSkills()).isEmpty();
        assertThat(score(context)).isEqualTo(0.0);
    }

    @Test
    void nullAndBlankEntriesAreIgnored() {
        Set<String> required = new LinkedHashSet<>();
        required.add(null);
        required.add("  ");
        required.add("Java");
        Set<String> verified = new LinkedHashSet<>();
        verified.add(null);
        verified.add("Java");

        MatchContext context = evaluate(student(Set.of(), verified), opportunity(required, null));

        assertThat(context.getMatchedSkills()).containsExactly("java");
        assertThat(context.getMissingSkills()).isEmpty();
    }

    @Test
    void claimedAndVerifiedOverlapCountsOnce() {
        MatchContext context = evaluate(
                student(Set.of("Java"), Set.of("Java")),
                opportunity(Set.of("Java"), Set.of()));

        assertThat(context.getMatchedSkills()).containsExactly("java");
        assertThat(score(context)).isCloseTo(expected(1), org.assertj.core.data.Offset.offset(1e-9));
    }
}
