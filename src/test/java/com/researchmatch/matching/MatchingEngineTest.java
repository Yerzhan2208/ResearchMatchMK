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
        assertThat(defaultScore).isBetween(0.0, 0.30);
    }
}
