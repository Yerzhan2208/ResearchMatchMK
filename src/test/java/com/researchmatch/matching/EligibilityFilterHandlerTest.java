package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityFilterHandlerTest {

    private final EligibilityFilterHandler handler = new EligibilityFilterHandler();

    private static StudentProfile eligibleStudent() {
        StudentProfile student = new StudentProfile();
        student.setEmail("student@example.com");
        student.setHoursPerWeek(10);
        student.setAcademicYear(2);
        return student;
    }

    private static Opportunity openOpportunity() {
        Opportunity opportunity = new Opportunity();
        opportunity.setBeginnerFriendly(true);
        opportunity.setHoursPerWeek(5);
        opportunity.setMinAcademicYear(1);
        return opportunity;
    }

    private MatchContext lastContext;

    private MatchLayerHandler.LayerResult runAndKeep(StudentProfile student, Opportunity opportunity) {
        lastContext = new MatchContext(student, opportunity);
        return handler.evaluate(lastContext);
    }

    // ---------------------------------------------------------------- email

    @Test
    void rejectsNullEmail() {
        StudentProfile student = eligibleStudent();
        student.setEmail(null);

        assertThat(runAndKeep(student, openOpportunity())).isEqualTo(MatchLayerHandler.LayerResult.STOP);
        assertThat(lastContext.isEligible()).isFalse();
        assertThat(lastContext.getExplanationSummary()).contains("email");
    }

    @Test
    void rejectsBlankEmail() {
        StudentProfile student = eligibleStudent();
        student.setEmail("   ");

        assertThat(runAndKeep(student, openOpportunity())).isEqualTo(MatchLayerHandler.LayerResult.STOP);
        assertThat(lastContext.isEligible()).isFalse();
    }

    // ---------------------------------------------------------------- hours

    @Test
    void rejectsZeroHours() {
        StudentProfile student = eligibleStudent();
        student.setHoursPerWeek(0);

        assertThat(runAndKeep(student, openOpportunity())).isEqualTo(MatchLayerHandler.LayerResult.STOP);
        assertThat(lastContext.isEligible()).isFalse();
    }

    @Test
    void rejectsNegativeHours() {
        StudentProfile student = eligibleStudent();
        student.setHoursPerWeek(-3);

        assertThat(runAndKeep(student, openOpportunity())).isEqualTo(MatchLayerHandler.LayerResult.STOP);
        assertThat(lastContext.isEligible()).isFalse();
    }

    @Test
    void rejectsNullHours() {
        StudentProfile student = eligibleStudent();
        student.setHoursPerWeek(null);

        assertThat(runAndKeep(student, openOpportunity())).isEqualTo(MatchLayerHandler.LayerResult.STOP);
        assertThat(lastContext.isEligible()).isFalse();
    }

    @Test
    void rejectsWhenAvailabilityBelowRequiredHours() {
        StudentProfile student = eligibleStudent();
        student.setHoursPerWeek(4);
        Opportunity opportunity = openOpportunity();
        opportunity.setHoursPerWeek(5);

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.STOP);
        assertThat(lastContext.isEligible()).isFalse();
        assertThat(lastContext.getExplanationSummary()).contains("4").contains("5");
    }

    @Test
    void acceptsWhenAvailabilityEqualsRequiredHours() {
        StudentProfile student = eligibleStudent();
        student.setHoursPerWeek(5);
        Opportunity opportunity = openOpportunity();
        opportunity.setHoursPerWeek(5);

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
        assertThat(lastContext.isEligible()).isTrue();
    }

    @Test
    void ignoresRequiredHoursWhenOpportunityDoesNotSetThem() {
        StudentProfile student = eligibleStudent();
        student.setHoursPerWeek(1);
        Opportunity opportunity = openOpportunity();
        opportunity.setHoursPerWeek(null);

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
    }

    // ---------------------------------------------------------------- academic year

    @Test
    void rejectsNullAcademicYearWhenMinimumSet() {
        StudentProfile student = eligibleStudent();
        student.setAcademicYear(null);
        Opportunity opportunity = openOpportunity();
        opportunity.setMinAcademicYear(2);

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.STOP);
        assertThat(lastContext.isEligible()).isFalse();
    }

    @Test
    void rejectsAcademicYearBelowMinimum() {
        StudentProfile student = eligibleStudent();
        student.setAcademicYear(1);
        Opportunity opportunity = openOpportunity();
        opportunity.setMinAcademicYear(2);

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.STOP);
        assertThat(lastContext.isEligible()).isFalse();
    }

    @Test
    void acceptsAcademicYearEqualToMinimum() {
        StudentProfile student = eligibleStudent();
        student.setAcademicYear(2);
        Opportunity opportunity = openOpportunity();
        opportunity.setMinAcademicYear(2);

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
    }

    @Test
    void rejectsNullAcademicYearWhenDefaultMinimumIsOne() {
        StudentProfile student = eligibleStudent();
        student.setAcademicYear(null);
        Opportunity opportunity = openOpportunity();
        opportunity.setMinAcademicYear(null);
        assertThat(opportunity.getMinAcademicYear()).isEqualTo(1);

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.STOP);
    }

    // ---------------------------------------------------------------- required skills

    @Test
    void rejectsNonBeginnerOpportunityWhenNoSkillOverlap() {
        StudentProfile student = eligibleStudent();
        student.setClaimedSkills(Set.of("Python"));
        student.setVerifiedSkills(Set.of());
        Opportunity opportunity = openOpportunity();
        opportunity.setBeginnerFriendly(false);
        opportunity.setRequiredSkills(Set.of("Java"));

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.STOP);
        assertThat(lastContext.isEligible()).isFalse();
        assertThat(lastContext.getExplanationSummary()).containsIgnoringCase("required skills");
    }

    @Test
    void acceptsWhenClaimedSkillOverlaps() {
        StudentProfile student = eligibleStudent();
        student.setClaimedSkills(Set.of("Java"));
        Opportunity opportunity = openOpportunity();
        opportunity.setBeginnerFriendly(false);
        opportunity.setRequiredSkills(Set.of("Java"));

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
    }

    @Test
    void acceptsWhenVerifiedSkillOverlaps() {
        StudentProfile student = eligibleStudent();
        student.setVerifiedSkills(Set.of("Java"));
        Opportunity opportunity = openOpportunity();
        opportunity.setBeginnerFriendly(false);
        opportunity.setRequiredSkills(Set.of("Java"));

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
    }

    @Test
    void skillComparisonIgnoresCaseAndWhitespace() {
        StudentProfile student = eligibleStudent();
        student.setClaimedSkills(Set.of("  jAvA "));
        Opportunity opportunity = openOpportunity();
        opportunity.setBeginnerFriendly(false);
        opportunity.setRequiredSkills(Set.of("JAVA"));

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
    }

    @Test
    void beginnerFriendlyOpportunitySkipsSkillRequirement() {
        StudentProfile student = eligibleStudent();
        student.setClaimedSkills(Set.of("Python"));
        Opportunity opportunity = openOpportunity();
        opportunity.setBeginnerFriendly(true);
        opportunity.setRequiredSkills(Set.of("Java"));

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
    }

    @Test
    void nonBeginnerOpportunityWithNoRequiredSkillsPasses() {
        StudentProfile student = eligibleStudent();
        Opportunity opportunity = openOpportunity();
        opportunity.setBeginnerFriendly(false);
        opportunity.setRequiredSkills(new LinkedHashSet<>());

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
    }

    @Test
    void nullSkillCollectionsAreTreatedAsEmpty() {
        StudentProfile student = eligibleStudent();
        student.setClaimedSkills(null);
        student.setVerifiedSkills(null);
        Opportunity opportunity = openOpportunity();
        opportunity.setBeginnerFriendly(false);
        opportunity.setRequiredSkills(Set.of("Java"));

        assertThat(runAndKeep(student, opportunity)).isEqualTo(MatchLayerHandler.LayerResult.STOP);
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void acceptsFullyEligibleStudent() {
        assertThat(runAndKeep(eligibleStudent(), openOpportunity()))
                .isEqualTo(MatchLayerHandler.LayerResult.CONTINUE);
        assertThat(lastContext.isEligible()).isTrue();
    }
}
