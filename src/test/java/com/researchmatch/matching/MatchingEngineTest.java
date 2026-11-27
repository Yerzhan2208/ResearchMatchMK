package com.researchmatch.matching;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResourceNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MatchingEngineTest {

    private StudentRepository students;
    private OpportunityRepository opportunities;
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        students = Mockito.mock(StudentRepository.class);
        opportunities = Mockito.mock(OpportunityRepository.class);
        engine = new MatchingEngine(students, opportunities, SystemConfigManager.getInstance(),
                List.of(new BalancedMatchStrategy()));
    }

    private static StudentProfile eligibleStudent() {
        StudentProfile student = new StudentProfile();
        student.setId(1L);
        student.setEmail("student@example.com");
        student.setHoursPerWeek(10);
        student.setAcademicYear(3);
        student.setInterests(Set.of("java", "spring"));
        student.setClaimedSkills(Set.of("java"));
        student.setVerifiedSkills(Set.of("spring"));
        return student;
    }

    private static Opportunity openOpportunity() {
        Opportunity opportunity = new Opportunity();
        opportunity.setId(2L);
        opportunity.setFacultyId(9L);
        opportunity.setTitle("Java Spring backend research");
        opportunity.setDescription("Build java spring services");
        opportunity.setBeginnerFriendly(true);
        opportunity.setHoursPerWeek(5);
        opportunity.setMinAcademicYear(1);
        opportunity.setRequiredSkills(Set.of("java"));
        return opportunity;
    }

    // ---------------------------------------------------------------- null / missing ids

    @Test
    void evaluateRejectsNullStudentId() {
        assertThatThrownBy(() -> engine.evaluate(null, 2L)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void evaluateRejectsNullOpportunityId() {
        assertThatThrownBy(() -> engine.evaluate(1L, null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void evaluateThrowsWhenStudentMissing() {
        when(students.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> engine.evaluate(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student 1");
    }

    @Test
    void evaluateThrowsWhenOpportunityMissing() {
        when(students.findById(1L)).thenReturn(Optional.of(eligibleStudent()));
        when(opportunities.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> engine.evaluate(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Opportunity 2");
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void evaluateBuildsEligibleResultThroughFullChain() {
        when(students.findById(1L)).thenReturn(Optional.of(eligibleStudent()));
        when(opportunities.findById(2L)).thenReturn(Optional.of(openOpportunity()));

        MatchResult result = engine.evaluate(1L, 2L);

        assertThat(result).isNotNull();
        assertThat(result.getStudentId()).isEqualTo(1L);
        assertThat(result.getOpportunityId()).isEqualTo(2L);
        assertThat(result.isEligible()).isTrue();
        assertThat(result.getMatchedSkills()).contains("java");
        assertThat(result.getMissingSkills()).isEmpty();
        assertThat(result.getExplanationSummary()).isNotBlank();
        assertThat(result.getRecommendedPrep()).isNotBlank();
        // Semantic weight (0.30) + evidence bonus (2.0 for one verified match) are both present.
        assertThat(result.getTotalScore()).isGreaterThan(0.0);
    }

    // ---------------------------------------------------------------- ineligible path

    @Test
    void evaluateBuildsIneligibleResultThatShortCircuitsExplanation() {
        StudentProfile student = eligibleStudent();
        student.setAcademicYear(1);
        Opportunity opportunity = openOpportunity();
        opportunity.setMinAcademicYear(3);

        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(opportunities.findById(2L)).thenReturn(Optional.of(opportunity));

        MatchResult result = engine.evaluate(1L, 2L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getExplanationSummary()).isNotBlank().contains("academic year");
        assertThat(result.getRecommendedPrep()).isNull();
        assertThat(result.getMatchedSkills()).isEmpty();
        assertThat(result.getMissingSkills()).isEmpty();
        assertThat(result.getTotalScore()).isEqualTo(0.0);
    }

    // ---------------------------------------------------------------- config sharing

    @Test
    void semanticHandlerSharesEngineConfig() {
        // The engine must forward its own SystemConfigManager to the semantic handler. A distinct,
        // non-default weight proves sharing: were the handler to fall back to the singleton (0.30),
        // the score would not scale by this custom weight.
        double customWeight = 0.90;
        SystemConfigManager sharedConfig = Mockito.mock(SystemConfigManager.class);
        when(sharedConfig.getDouble(eq(SystemConfigManager.KEY_SEMANTIC_WEIGHT), anyDouble()))
                .thenReturn(customWeight);
        when(sharedConfig.getDouble(eq(SystemConfigManager.KEY_EVIDENCE_WEIGHT), anyDouble()))
                .thenReturn(1.0 - customWeight);

        StudentProfile student = eligibleStudent();
        Opportunity opportunity = openOpportunity();
        opportunity.setRequiredSkills(Set.of());
        opportunity.setBeginnerFriendly(true);

        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(opportunities.findById(2L)).thenReturn(Optional.of(opportunity));

        MatchingEngine withSharedConfig = new MatchingEngine(students, opportunities, sharedConfig,
                List.of(new BalancedMatchStrategy()));
        double sharedScore = withSharedConfig.evaluate(1L, 2L).getTotalScore();

        MatchingEngine withDefaultConfig = new MatchingEngine(students, opportunities,
                SystemConfigManager.getInstance(), List.of(new BalancedMatchStrategy()));
        double defaultScore = withDefaultConfig.evaluate(1L, 2L).getTotalScore();

        // No required skills ⇒ no evidence bonus; total equals the semantic contribution.
        assertThat(sharedScore).isBetween(0.0, customWeight);
        assertThat(sharedScore).isGreaterThan(defaultScore);
        assertThat(defaultScore).isBetween(0.0, ScoringWeights.DEFAULT_SEMANTIC_WEIGHT);
    }

    // ---------------------------------------------------------------- rankOpportunities

    private MatchingEngine engineWithAllStrategies() {
        return new MatchingEngine(students, opportunities, SystemConfigManager.getInstance(),
                List.of(new BalancedMatchStrategy(), new BeginnerFriendlyStrategy(), new SkillStrictStrategy()));
    }

    private static Opportunity opportunity(long id, boolean beginnerFriendly, Set<String> required, double noScore) {
        Opportunity opportunity = new Opportunity();
        opportunity.setId(id);
        opportunity.setFacultyId(9L);
        opportunity.setTitle("Java spring research " + id);
        opportunity.setDescription("java spring");
        opportunity.setBeginnerFriendly(beginnerFriendly);
        opportunity.setHoursPerWeek(5);
        opportunity.setMinAcademicYear(1);
        opportunity.setRequiredSkills(required);
        return opportunity;
    }

    @Test
    void rankOpportunitiesRejectsNullStudentId() {
        assertThatThrownBy(() -> engineWithAllStrategies().rankOpportunities(null, "balanced"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rankOpportunitiesThrowsWhenStudentMissing() {
        when(students.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engineWithAllStrategies().rankOpportunities(1L, "balanced"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student 1");
    }

    @Test
    void rankOpportunitiesRejectsUnknownStrategy() {
        when(students.findById(1L)).thenReturn(Optional.of(eligibleStudent()));

        assertThatThrownBy(() -> engineWithAllStrategies().rankOpportunities(1L, "nonsense"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rankOpportunitiesReturnsEmptyWhenNoOpportunities() {
        when(students.findById(1L)).thenReturn(Optional.of(eligibleStudent()));
        when(opportunities.findAll()).thenReturn(List.of());

        assertThat(engineWithAllStrategies().rankOpportunities(1L, "balanced")).isEmpty();
    }

    @Test
    void rankOpportunitiesBalancedConsidersAllOpportunities() {
        when(students.findById(1L)).thenReturn(Optional.of(eligibleStudent()));
        when(opportunities.findAll()).thenReturn(List.of(
                opportunity(10L, false, Set.of(), 0),
                opportunity(20L, true, Set.of(), 0)));

        List<MatchResult> ranked = engineWithAllStrategies().rankOpportunities(1L, "balanced");

        assertThat(ranked).extracting(MatchResult::getOpportunityId).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void rankOpportunitiesSkillStrictConsidersAllOpportunities() {
        when(students.findById(1L)).thenReturn(Optional.of(eligibleStudent()));
        when(opportunities.findAll()).thenReturn(List.of(
                opportunity(10L, false, Set.of(), 0),
                opportunity(20L, true, Set.of(), 0)));

        List<MatchResult> ranked = engineWithAllStrategies().rankOpportunities(1L, "skill-strict");

        assertThat(ranked).extracting(MatchResult::getOpportunityId).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void rankOpportunitiesBeginnerFriendlyOnlyConsidersBeginnerOpportunities() {
        when(students.findById(1L)).thenReturn(Optional.of(eligibleStudent()));
        Opportunity beginner = opportunity(20L, true, Set.of(), 0);
        when(opportunities.findByBeginnerFriendlyTrue()).thenReturn(List.of(beginner));

        List<MatchResult> ranked = engineWithAllStrategies().rankOpportunities(1L, "beginner-friendly");

        assertThat(ranked).extracting(MatchResult::getOpportunityId).containsExactly(20L);
        Mockito.verify(opportunities).findByBeginnerFriendlyTrue();
        Mockito.verify(opportunities, Mockito.never()).findAll();
    }

    @Test
    void rankOpportunitiesDefaultsToBalancedForNullAndBlank() {
        when(students.findById(1L)).thenReturn(Optional.of(eligibleStudent()));
        when(opportunities.findAll()).thenReturn(List.of(
                opportunity(20L, false, Set.of(), 0),
                opportunity(10L, false, Set.of(), 0)));

        List<MatchResult> explicit = engineWithAllStrategies().rankOpportunities(1L, "balanced");
        List<MatchResult> nullName = engineWithAllStrategies().rankOpportunities(1L, null);
        List<MatchResult> blankName = engineWithAllStrategies().rankOpportunities(1L, "   ");

        List<Long> explicitOrder = explicit.stream().map(MatchResult::getOpportunityId).toList();
        assertThat(explicitOrder).isNotEmpty();
        assertThat(nullName.stream().map(MatchResult::getOpportunityId).toList()).isEqualTo(explicitOrder);
        assertThat(blankName.stream().map(MatchResult::getOpportunityId).toList()).isEqualTo(explicitOrder);
    }

    @Test
    void rankOpportunitiesExcludesIneligibleResults() {
        // Student is in year 1; only the opportunity requiring year 3 is ineligible.
        StudentProfile student = eligibleStudent();
        student.setAcademicYear(1);
        when(students.findById(1L)).thenReturn(Optional.of(student));

        Opportunity eligible = opportunity(10L, true, Set.of(), 0);
        eligible.setMinAcademicYear(1);
        Opportunity ineligible = opportunity(20L, true, Set.of(), 0);
        ineligible.setMinAcademicYear(3);
        when(opportunities.findAll()).thenReturn(List.of(eligible, ineligible));

        List<MatchResult> ranked = engineWithAllStrategies().rankOpportunities(1L, "balanced");

        assertThat(ranked).extracting(MatchResult::getOpportunityId).containsExactly(10L);
        assertThat(ranked).allMatch(MatchResult::isEligible);
    }

    @Test
    void rankOpportunitiesRespectsMaxResults() {
        when(students.findById(1L)).thenReturn(Optional.of(eligibleStudent()));
        when(opportunities.findAll()).thenReturn(List.of(
                opportunity(1L, false, Set.of(), 0),
                opportunity(2L, false, Set.of(), 0),
                opportunity(3L, false, Set.of(), 0)));

        SystemConfigManager cappedConfig = Mockito.mock(SystemConfigManager.class);
        when(cappedConfig.getInt(eq(SystemConfigManager.KEY_MAX_MATCH_RESULTS), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(2);
        MatchingEngine capped = new MatchingEngine(students, opportunities, cappedConfig,
                List.of(new BalancedMatchStrategy(), new BeginnerFriendlyStrategy(), new SkillStrictStrategy()));

        List<MatchResult> ranked = capped.rankOpportunities(1L, "balanced");

        assertThat(ranked).hasSize(2);
    }
}
