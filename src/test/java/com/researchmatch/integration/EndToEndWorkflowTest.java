package com.researchmatch.integration;

import com.researchmatch.config.IllegalStateTransitionException;
import com.researchmatch.controllers.ResearchMatchFacade;
import com.researchmatch.models.Application;
import com.researchmatch.models.AuditEvent;
import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.SkillEvidence;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end and boundary-value tests (Member 5) that drive the whole system through the public
 * {@link ResearchMatchFacade} against the real Spring context and H2 database.
 *
 * <p>Each test creates its own uniquely-identified student/faculty/opportunity so independent tests never
 * collide. The test database is seeded by {@code DataSeeder}, so it is never assumed empty.
 */
@SpringBootTest
class EndToEndWorkflowTest {

    @Autowired
    private ResearchMatchFacade facade;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private static String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@my.cityu.edu.hk";
    }

    private StudentProfile newStudent(int academicYear, int hoursPerWeek) {
        StudentProfile student = new StudentProfile();
        student.setName("Test Student " + UUID.randomUUID());
        student.setEmail(uniqueEmail("student"));
        student.setMajor("Computer Science");
        student.setAcademicYear(academicYear);
        student.setHoursPerWeek(hoursPerWeek);
        student.setGithubUsername("java-wizard");
        return facade.saveStudent(student);
    }

    private FacultyProfile newFaculty() {
        FacultyProfile faculty = new FacultyProfile();
        faculty.setName("Prof. Test " + UUID.randomUUID());
        faculty.setDepartment("Computer Science");
        faculty.setResearchAreas(new LinkedHashSet<>(Set.of("Natural Language Processing")));
        return facade.getFacultyRepository().save(faculty);
    }

    private Opportunity newOpportunity(Long facultyId, int hoursPerWeek, int minAcademicYear,
                                       boolean beginnerFriendly, Set<String> requiredSkills) {
        Opportunity opportunity = new Opportunity();
        opportunity.setFacultyId(facultyId);
        opportunity.setTitle("Opportunity " + UUID.randomUUID());
        opportunity.setDescription("A research opportunity for end-to-end testing.");
        opportunity.setRequiredSkills(new LinkedHashSet<>(requiredSkills));
        opportunity.setHoursPerWeek(hoursPerWeek);
        opportunity.setCompensationType("CREDIT");
        opportunity.setBeginnerFriendly(beginnerFriendly);
        opportunity.setMaxApplicants(3);
        opportunity.setMinAcademicYear(minAcademicYear);
        return facade.saveOpportunity(opportunity);
    }

    // ---------------------------------------------------------------- full happy path

    @Test
    void fullFlow_importSkills_rank_match_apply_and_lifecycle_withAudit() {
        StudentProfile student = newStudent(3, 12);
        FacultyProfile faculty = newFaculty();
        Opportunity opportunity = newOpportunity(faculty.getId(), 8, 1, true, Set.of("Python"));

        // 1. Import GitHub skills (deterministic offline adapter; handle contains "java" -> Java).
        List<SkillEvidence> imported = facade.importSkills(student.getId(), "github", "java-wizard");
        assertThat(imported).isNotEmpty();
        assertThat(imported).allSatisfy(row -> {
            assertThat(row.getStudentId()).isEqualTo(student.getId());
            assertThat(row.isVerifiedByStudent()).isTrue();
        });
        List<String> skillNames = imported.stream().map(SkillEvidence::getSkillName).toList();
        assertThat(skillNames).contains("Java");

        // Imported skills are promoted to verified skills on the profile, visible to matching.
        StudentProfile reloaded = facade.getStudent(student.getId());
        assertThat(reloaded.getVerifiedSkills()).contains("Java");

        // 2. Run the matching engine -> ranked opportunities with explanations.
        List<MatchResult> ranked = facade.recommend(student.getId(), "balanced");
        assertThat(ranked).isNotEmpty();
        assertThat(ranked).allSatisfy(result -> {
            assertThat(result.getStudentId()).isEqualTo(student.getId());
            assertThat(result.isEligible()).isTrue();
            assertThat(result.getExplanationSummary()).isNotBlank();
        });
        // The freshly-created opportunity must be part of the eligible ranking.
        assertThat(ranked).extracting(MatchResult::getOpportunityId).contains(opportunity.getId());

        // Single-pair evaluation carries the same explanation contract.
        MatchResult single = facade.match(student.getId(), opportunity.getId());
        assertThat(single.isEligible()).isTrue();
        assertThat(single.getExplanationSummary()).isNotBlank();

        // 3. Submit an application, then shortlist -> interview -> accept.
        Application application = facade.submitApplication(student.getId(), opportunity.getId(),
                "I would love to contribute to this project.");
        assertThat(application.getId()).isNotNull();
        assertThat(application.getStatus()).isEqualTo(Application.Status.NEW);

        Application shortlisted = facade.shortlist(application.getId());
        assertThat(shortlisted.getStatus()).isEqualTo(Application.Status.SHORTLISTED);

        Application interview = facade.scheduleInterview(application.getId());
        assertThat(interview.getStatus()).isEqualTo(Application.Status.INTERVIEW);

        Application accepted = facade.accept(application.getId());
        assertThat(accepted.getStatus()).isEqualTo(Application.Status.ACCEPTED);

        // 4. The audit trail records every transition (submit + shortlist + interview + accept).
        List<AuditEvent> events =
                auditEventRepository.findByEntityIdOrderByTimestampDesc(application.getId());
        assertThat(events).hasSize(4);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getAction()).isEqualTo("APPLICATION_STATUS_CHANGED");
            assertThat(event.getEntityId()).isEqualTo(application.getId());
        });
        String allDetails = events.stream().map(AuditEvent::getDetails).reduce("", (a, b) -> a + " | " + b);
        assertThat(allDetails)
                .contains("NEW to NEW")                    // submit
                .contains("NEW to SHORTLISTED")            // shortlist
                .contains("SHORTLISTED to INTERVIEW")      // interview
                .contains("INTERVIEW to ACCEPTED");        // accept
    }

    // ---------------------------------------------------------------- privacy enforcement

    @Test
    void discoverCandidates_neverReturnsStudentsNotSearchableByFaculty() {
        FacultyProfile faculty = newFaculty();
        Opportunity opportunity = newOpportunity(faculty.getId(), 6, 1, true, Set.of("Python"));

        StudentProfile hidden = newStudent(2, 10);
        // Default privacy: must be non-searchable (never opt in implicitly).
        assertThat(hidden.isSearchableByFaculty()).isFalse();

        StudentProfile visible = newStudent(2, 10);
        visible.setSearchableByFaculty(true);
        visible = facade.saveStudent(visible);

        // Both apply; the filter must ignore privacy-insensitive application activity.
        Application hiddenApp = facade.submitApplication(hidden.getId(), opportunity.getId(), "statement");
        facade.submitApplication(visible.getId(), opportunity.getId(), "statement");
        assertThat(hiddenApp.getStatus()).isEqualTo(Application.Status.NEW);

        List<StudentProfile> candidates = facade.discoverCandidates(faculty.getId());

        assertThat(candidates).extracting(StudentProfile::getId)
                .contains(visible.getId())
                .doesNotContain(hidden.getId());
        assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.isSearchableByFaculty()).isTrue());
    }

    // ---------------------------------------------------------------- equivalence partitioning + BVA
    // Hours: below / at / above the opportunity's required weekly hours.

    @Test
    void hoursEquivalencePartitions_andBoundaries() {
        FacultyProfile faculty = newFaculty();
        int required = 10;
        Opportunity opportunity = newOpportunity(faculty.getId(), required, 1, true, Set.of("Python"));

        // below the boundary (required - 1) -> ineligible
        StudentProfile below = newStudent(2, required - 1);
        assertThat(facade.match(below.getId(), opportunity.getId()).isEligible()).isFalse();

        // exactly at the boundary -> eligible
        StudentProfile at = newStudent(2, required);
        assertThat(facade.match(at.getId(), opportunity.getId()).isEligible()).isTrue();

        // above the boundary (required + 1) -> eligible
        StudentProfile above = newStudent(2, required + 1);
        assertThat(facade.match(above.getId(), opportunity.getId()).isEligible()).isTrue();
    }

    // Academic year: minAcademicYear partitions with boundary values.

    @Test
    void academicYearEquivalencePartitions_andBoundaries() {
        FacultyProfile faculty = newFaculty();
        int minYear = 3;
        Opportunity opportunity = newOpportunity(faculty.getId(), 5, minYear, true, Set.of("Python"));

        // below minimum (minYear - 1) -> ineligible
        StudentProfile lower = newStudent(minYear - 1, 10);
        assertThat(facade.match(lower.getId(), opportunity.getId()).isEligible()).isFalse();

        // exactly the minimum -> eligible
        StudentProfile exact = newStudent(minYear, 10);
        assertThat(facade.match(exact.getId(), opportunity.getId()).isEligible()).isTrue();

        // above the minimum (minYear + 1) -> eligible
        StudentProfile higher = newStudent(minYear + 1, 10);
        assertThat(facade.match(higher.getId(), opportunity.getId()).isEligible()).isTrue();

        // unspecified academic year (null) -> ineligible when a minimum is required
        StudentProfile unknownYear = newStudent(1, 10);
        unknownYear.setAcademicYear(null);
        unknownYear = facade.saveStudent(unknownYear);
        assertThat(facade.match(unknownYear.getId(), opportunity.getId()).isEligible()).isFalse();
    }

    // ---------------------------------------------------------------- invalid pipeline transitions

    @Test
    void invalidTransitions_throwAndDoNotChangeStateOrAudit() {
        StudentProfile student = newStudent(3, 10);
        FacultyProfile faculty = newFaculty();
        Opportunity opportunity = newOpportunity(faculty.getId(), 5, 1, true, Set.of("Python"));

        Application application = facade.submitApplication(student.getId(), opportunity.getId(), "statement");
        Long appId = application.getId();
        int auditAfterSubmit = auditEventRepository.findByEntityIdOrderByTimestampDesc(appId).size();
        assertThat(auditAfterSubmit).isEqualTo(1);

        // Interview before shortlist is disallowed (NEW -> INTERVIEW).
        assertThatThrownBy(() -> facade.scheduleInterview(appId))
                .isInstanceOf(IllegalStateTransitionException.class);
        // Accept straight from NEW is disallowed.
        assertThatThrownBy(() -> facade.accept(appId))
                .isInstanceOf(IllegalStateTransitionException.class);

        // State unchanged and no audit events written by rejected attempts.
        assertThat(facade.getApplicationRepository().findById(appId).orElseThrow().getStatus())
                .isEqualTo(Application.Status.NEW);
        assertThat(auditEventRepository.findByEntityIdOrderByTimestampDesc(appId)).hasSize(1);

        // Move legally to ACCEPTED.
        facade.shortlist(appId);
        facade.scheduleInterview(appId);
        facade.accept(appId);

        int auditAtAccepted = auditEventRepository.findByEntityIdOrderByTimestampDesc(appId).size();
        assertThat(auditAtAccepted).isEqualTo(4);

        // ACCEPTED is terminal: further transitions are rejected without side effects.
        assertThatThrownBy(() -> facade.shortlist(appId))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> facade.reject(appId))
                .isInstanceOf(IllegalStateTransitionException.class);

        assertThat(facade.getApplicationRepository().findById(appId).orElseThrow().getStatus())
                .isEqualTo(Application.Status.ACCEPTED);
        assertThat(auditEventRepository.findByEntityIdOrderByTimestampDesc(appId)).hasSize(4);
    }

    // ---------------------------------------------------------------- ranking strategy coverage

    @Test
    void recommend_supportsEveryStrategy_andRejectsUnknown() {
        StudentProfile student = newStudent(3, 12);
        newFaculty();

        for (String strategy : Arrays.asList("balanced", "beginner-friendly", "skill-strict", null, "  ")) {
            List<MatchResult> ranked = facade.recommend(student.getId(), strategy);
            assertThat(ranked).isNotNull();
            assertThat(ranked).allSatisfy(result -> assertThat(result.getStudentId()).isEqualTo(student.getId()));
        }

        assertThatThrownBy(() -> facade.recommend(student.getId(), "does-not-exist"))
                .isInstanceOf(com.researchmatch.config.InvalidRequestException.class);
    }
}
