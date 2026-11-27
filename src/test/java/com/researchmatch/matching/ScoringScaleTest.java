package com.researchmatch.matching;

import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.StudentRepository;
import com.researchmatch.repository.SystemConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * End-to-end scoring-scale tests for issues #19 and #20.
 *
 * <p>The engine is driven through its real handler chain (Eligibility → Semantic → Evidence →
 * Explanation) with the default 0.7/0.3 weights, so these assertions pin down the actual
 * {@code totalScore} the UI/ranking consume, not just a single layer.
 */
class ScoringScaleTest {

    private StudentRepository students;
    private OpportunityRepository opportunities;
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        students = Mockito.mock(StudentRepository.class);
        opportunities = Mockito.mock(OpportunityRepository.class);
        engine = new MatchingEngine(students, opportunities, SystemConfigManager.getInstance(),
                List.of(new BalancedMatchStrategy(), new BeginnerFriendlyStrategy(), new SkillStrictStrategy()));
    }

    /** An eligible student (has email, availability, year) with the given semantic/skill profile. */
    private static StudentProfile student(Set<String> interests, Set<String> claimed, Set<String> verified) {
        StudentProfile student = new StudentProfile();
        student.setId(1L);
        student.setEmail("student@example.com");
        student.setHoursPerWeek(20);
        student.setAcademicYear(3);
        student.setInterests(interests);
        student.setClaimedSkills(claimed);
        student.setVerifiedSkills(verified);
        return student;
    }

    private static Opportunity opportunity(long id, String title, String description,
                                            Set<String> required) {
        Opportunity opportunity = new Opportunity();
        opportunity.setId(id);
        opportunity.setFacultyId(9L);
        opportunity.setTitle(title);
        opportunity.setDescription(description);
        opportunity.setBeginnerFriendly(true);
        opportunity.setHoursPerWeek(5);
        opportunity.setMinAcademicYear(1);
        opportunity.setRequiredSkills(required);
        return opportunity;
    }

    private double evaluateScore(StudentProfile student, Opportunity opportunity) {
        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(opportunities.findById(opportunity.getId())).thenReturn(Optional.of(opportunity));
        MatchResult result = engine.evaluate(1L, opportunity.getId());
        return result.getTotalScore();
    }

    // ---------------------------------------------------------------- 1. semantic-only

    @Test
    void strongSemanticMatchScoresPositiveEvenWithoutVerifiedSkills() {
        StudentProfile student = student(Set.of("java", "spring"), Set.of(), Set.of());
        Opportunity opp = opportunity(2L, "Java spring backend", "build java spring services", Set.of());

        double score = evaluateScore(student, opp);

        assertThat(score).isGreaterThan(0.0);
        assertThat(score).isLessThanOrEqualTo(ScoringWeights.DEFAULT_SEMANTIC_WEIGHT + 1e-9);
    }

    // ---------------------------------------------------------------- 2. evidence-only

    @Test
    void verifiedSkillEvidenceScoresPositiveEvenWithoutSemanticOverlap() {
        // Verified skill matches a required skill; title/description share no terms with student text.
        StudentProfile student = student(Set.of(), Set.of(), Set.of("kotlin"));
        Opportunity opp = opportunity(2L, "Quantum chromodynamics", "lattice field theory", Set.of("Kotlin"));

        double score = evaluateScore(student, opp);

        assertThat(score).isGreaterThan(0.0);
        // One verified skill ⇒ (2/10) * 0.3 = 0.06.
        assertThat(score).isCloseTo(0.06, org.assertj.core.data.Offset.offset(1e-9));
    }

    // ---------------------------------------------------------------- 3. combined + bounds

    @Test
    void combinedMatchStaysWithinUnitInterval() {
        StudentProfile student = student(Set.of("java"), Set.of(), Set.of("java"));
        Opportunity opp = opportunity(2L, "Java backend", "java services in java", Set.of("Java"));

        double score = evaluateScore(student, opp);

        assertThat(score).isBetween(0.0, 1.0);
        assertThat(score).isGreaterThan(0.0);
    }

    @Test
    void everyComponentContributionIsWithinUnitInterval() {
        StudentProfile student = student(Set.of("java"), Set.of(), Set.of("java"));
        Opportunity opp = opportunity(2L, "Java backend", "java services", Set.of("Java"));

        MatchContext context = new MatchContext(student, opp);
        MatchLayerHandler.LayerResult semantic = new SemanticMatchHandler().evaluate(context);
        MatchLayerHandler.LayerResult evidence = new EvidenceScoreHandler().evaluate(context);

        assertThat(semantic).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
        assertThat(evidence).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
        assertThat(context.getLayerScores().get(SemanticMatchHandler.LAYER)).isBetween(0.0, 1.0);
        assertThat(context.getLayerScores().get(EvidenceScoreHandler.LAYER)).isBetween(0.0, 1.0);
    }

    // ---------------------------------------------------------------- 4. semantic outranks one verified

    @Test
    void strongSemanticMatchOutranksPairWithOnlyOneVerifiedSkill() {
        // A: perfect semantic overlap, no verified evidence.
        StudentProfile semanticStudent = student(Set.of("java spring"), Set.of(), Set.of());
        Opportunity semanticOpp = opportunity(2L, "java spring", "java spring", Set.of());
        double semanticScore = evaluateScore(semanticStudent, semanticOpp);

        // B: no semantic overlap, exactly one verified required skill.
        StudentProfile evidenceStudent = student(Set.of(), Set.of(), Set.of("kotlin"));
        Opportunity evidenceOpp = opportunity(3L, "Quantum chromodynamics", "lattice theory", Set.of("Kotlin"));
        double evidenceScore = evaluateScore(evidenceStudent, evidenceOpp);

        assertThat(semanticScore).isGreaterThan(evidenceScore);
    }

    // ---------------------------------------------------------------- 5. zero-signal

    @Test
    void noSemanticOrEvidenceSignalScoresExactlyZero() {
        StudentProfile student = student(Set.of(), Set.of(), Set.of());
        Opportunity opp = opportunity(2L, "Quantum chromodynamics", "lattice field theory", Set.of("Kotlin"));

        // Kotlin required but the student has nothing ⇒ zero overlap, zero evidence. Beginner-friendly so
        // the missing skill does not make the pair ineligible (that is the eligibility layer's job).
        double score = evaluateScore(student, opp);

        assertThat(score).isEqualTo(0.0);
    }

    // ---------------------------------------------------------------- 6. ineligible excluded

    @Test
    void ineligiblePairsAreExcludedFromRanking() {
        // Year 1 student, opportunity requires year 3 ⇒ ineligible despite matching text.
        StudentProfile student = student(Set.of("java"), Set.of("java"), Set.of());
        student.setAcademicYear(1);
        Opportunity tooSenior = opportunity(2L, "Java backend", "java services", Set.of("Java"));
        tooSenior.setMinAcademicYear(3);
        Opportunity beginner = opportunity(3L, "Python scripting", "python tooling", Set.of("Python"));

        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(opportunities.findAll()).thenReturn(List.of(tooSenior, beginner));

        List<MatchResult> ranked = engine.rankOpportunities(1L, "balanced");

        assertThat(ranked).extracting(MatchResult::getOpportunityId).doesNotContain(2L);
        assertThat(ranked).allMatch(MatchResult::isEligible);
    }

    // ---------------------------------------------------------------- 7. ranking + tie-break

    @Test
    void rankingOrdersByScoreDescendingThenIdAscending() {
        StudentProfile student = student(Set.of("java"), Set.of("java"), Set.of());
        Opportunity strong = opportunity(5L, "Java backend", "java java services", Set.of("Java"));
        Opportunity weak = opportunity(4L, "Python scripting", "python tooling", Set.of("Python"));

        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(opportunities.findAll()).thenReturn(List.of(weak, strong));

        List<MatchResult> ranked = engine.rankOpportunities(1L, "balanced");

        assertThat(ranked).isNotEmpty();
        // Strong (higher semantic + evidence) must come first; weak has zero signal and ties broken by id.
        assertThat(ranked.get(0).getOpportunityId()).isEqualTo(5L);
        assertThat(ranked.get(0).getTotalScore())
                .isGreaterThanOrEqualTo(ranked.get(ranked.size() - 1).getTotalScore());
    }

    @Test
    void equalScoresTieBreakByOpportunityIdAscending() {
        StudentProfile student = student(Set.of(), Set.of(), Set.of());
        Opportunity a = opportunity(7L, "Quantum chromodynamics", "lattice theory", Set.of());
        Opportunity b = opportunity(3L, "Quantum chromodynamics", "lattice theory", Set.of());

        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(opportunities.findAll()).thenReturn(List.of(a, b));

        List<MatchResult> ranked = engine.rankOpportunities(1L, "balanced");

        assertThat(ranked).extracting(MatchResult::getOpportunityId).containsExactly(3L, 7L);
    }
}
