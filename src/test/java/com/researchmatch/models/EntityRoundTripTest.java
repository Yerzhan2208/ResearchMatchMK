package com.researchmatch.models;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trip coverage for every entity: setters, getters and equality semantics. */
class EntityRoundTripTest {

    @Test
    void studentProfileRoundTrips() {
        StudentProfile profile = new StudentProfile();
        profile.setId(1L);
        profile.setName("Ann");
        profile.setEmail("ann@cityu.edu.hk");
        profile.setMajor("Computer Science");
        profile.setAcademicYear(3);
        profile.setHoursPerWeek(12);
        profile.setGithubUsername("ann-dev");

        StudentProfile twin = new StudentProfile(1L, "Ann", "ann@cityu.edu.hk", "CS", 3, 12, "ann-dev");

        assertThat(profile.getId()).isEqualTo(1L);
        assertThat(profile.getName()).isEqualTo("Ann");
        assertThat(profile.getEmail()).isEqualTo("ann@cityu.edu.hk");
        assertThat(profile.getMajor()).isEqualTo("Computer Science");
        assertThat(profile.getAcademicYear()).isEqualTo(3);
        assertThat(profile.getHoursPerWeek()).isEqualTo(12);
        assertThat(profile.getGithubUsername()).isEqualTo("ann-dev");
        assertThat(profile).isEqualTo(twin).hasSameHashCodeAs(twin).isNotEqualTo(new StudentProfile());
        assertThat(new StudentProfile().equals(new Object())).isFalse();
    }

    @Test
    void facultyProfileRoundTrips() {
        FacultyProfile faculty = new FacultyProfile();
        faculty.setId(7L);
        faculty.setName("Dr. Lee");
        faculty.setDepartment("CS");
        faculty.setResearchAreas(Set.of("NLP"));
        faculty.setOpenAlexId("A123");

        FacultyProfile twin = new FacultyProfile(7L, "Dr. Lee", "CS", Set.of("NLP"), "A123");

        assertThat(faculty.getId()).isEqualTo(7L);
        assertThat(faculty.getName()).isEqualTo("Dr. Lee");
        assertThat(faculty.getDepartment()).isEqualTo("CS");
        assertThat(faculty.getResearchAreas()).containsExactly("NLP");
        assertThat(faculty.getOpenAlexId()).isEqualTo("A123");
        assertThat(faculty).isEqualTo(twin).hasSameHashCodeAs(twin).isNotEqualTo(new FacultyProfile());
    }

    @Test
    void opportunityRoundTrips() {
        Opportunity opportunity = new Opportunity();
        opportunity.setId(2L);
        opportunity.setFacultyId(7L);
        opportunity.setTitle("NLP research");
        opportunity.setDescription("Work on transformers");
        opportunity.setRequiredSkills(Set.of("Java"));
        opportunity.setPreferredSkills(Set.of("Python"));
        opportunity.setHoursPerWeek(10);
        opportunity.setCompensationType("CREDIT");
        opportunity.setBeginnerFriendly(true);
        opportunity.setMaxApplicants(3);

        Opportunity twin = new Opportunity(2L, 7L, "NLP research", "Work on transformers");

        assertThat(opportunity.getId()).isEqualTo(2L);
        assertThat(opportunity.getFacultyId()).isEqualTo(7L);
        assertThat(opportunity.getTitle()).isEqualTo("NLP research");
        assertThat(opportunity.getDescription()).isEqualTo("Work on transformers");
        assertThat(opportunity.getRequiredSkills()).containsExactly("Java");
        assertThat(opportunity.getPreferredSkills()).containsExactly("Python");
        assertThat(opportunity.getHoursPerWeek()).isEqualTo(10);
        assertThat(opportunity.getCompensationType()).isEqualTo("CREDIT");
        assertThat(opportunity.isBeginnerFriendly()).isTrue();
        assertThat(opportunity.getMaxApplicants()).isEqualTo(3);
        assertThat(opportunity).isEqualTo(twin).hasSameHashCodeAs(twin).isNotEqualTo(new Opportunity());
    }

    @Test
    void applicationRoundTripsAndStampsUpdatedAt() {
        Application application = new Application();
        application.setId(3L);
        application.setStudentId(1L);
        application.setOpportunityId(2L);
        application.setStatus(Application.Status.SHORTLISTED);
        application.setStatement("I am keen");
        application.setEvidenceLinks(Set.of("https://github.com/a/b"));
        application.setNotes("strong");
        application.touch();

        Application twin = new Application(3L, 1L, 2L);

        assertThat(application.getId()).isEqualTo(3L);
        assertThat(application.getStudentId()).isEqualTo(1L);
        assertThat(application.getOpportunityId()).isEqualTo(2L);
        assertThat(application.getStatus()).isEqualTo(Application.Status.SHORTLISTED);
        assertThat(application.getStatement()).isEqualTo("I am keen");
        assertThat(application.getEvidenceLinks()).containsExactly("https://github.com/a/b");
        assertThat(application.getNotes()).isEqualTo("strong");
        assertThat(application.getUpdatedAt()).isNotNull();
        assertThat(application).isEqualTo(twin).hasSameHashCodeAs(twin).isNotEqualTo(new Application());

        Application fresh = new Application();
        assertThat(fresh.getStatus()).isEqualTo(Application.Status.NEW);
        assertThat(Application.Status.values())
                .containsExactly(Application.Status.NEW, Application.Status.SHORTLISTED,
                        Application.Status.INTERVIEW, Application.Status.ACCEPTED, Application.Status.REJECTED);
    }

    @Test
    void skillEvidenceRoundTrips() {
        SkillEvidence evidence = new SkillEvidence();
        evidence.setId(4L);
        evidence.setStudentId(1L);
        evidence.setSkillName("Java");
        evidence.setRepoName("repo");
        evidence.setEvidenceSnippet("class Main {}");
        evidence.setVerifiedByStudent(true);

        SkillEvidence twin = new SkillEvidence(4L, 1L, "Java", "repo", "class Main {}");

        assertThat(evidence.getId()).isEqualTo(4L);
        assertThat(evidence.getStudentId()).isEqualTo(1L);
        assertThat(evidence.getSkillName()).isEqualTo("Java");
        assertThat(evidence.getRepoName()).isEqualTo("repo");
        assertThat(evidence.getEvidenceSnippet()).isEqualTo("class Main {}");
        assertThat(evidence.isVerifiedByStudent()).isTrue();
        assertThat(evidence).isEqualTo(twin).hasSameHashCodeAs(twin).isNotEqualTo(new SkillEvidence());
    }

    @Test
    void matchResultRoundTrips() {
        MatchResult result = new MatchResult();
        result.setId(5L);
        result.setStudentId(1L);
        result.setOpportunityId(2L);
        result.setTotalScore(88.5);
        result.setEligible(true);
        result.setMatchedSkills(Set.of("Java"));
        result.setMissingSkills(Set.of("Python"));
        result.setExplanationSummary("Good fit");
        result.setRecommendedPrep("Learn PyTorch");

        MatchResult twin = new MatchResult(5L, 1L, 2L);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getStudentId()).isEqualTo(1L);
        assertThat(result.getOpportunityId()).isEqualTo(2L);
        assertThat(result.getTotalScore()).isEqualTo(88.5);
        assertThat(result.isEligible()).isTrue();
        assertThat(result.getMatchedSkills()).containsExactly("Java");
        assertThat(result.getMissingSkills()).containsExactly("Python");
        assertThat(result.getExplanationSummary()).isEqualTo("Good fit");
        assertThat(result.getRecommendedPrep()).isEqualTo("Learn PyTorch");
        assertThat(result).isEqualTo(twin).hasSameHashCodeAs(twin).isNotEqualTo(new MatchResult());
    }

    @Test
    void auditEventRoundTripsAndStampsTimestamp() {
        AuditEvent event = new AuditEvent();
        event.setId(6L);
        event.setEntityId(3L);
        event.setAction("STATUS_CHANGED");
        event.setDetails("NEW -> SHORTLISTED");
        event.stamp();

        AuditEvent twin = new AuditEvent(6L, 3L, "STATUS_CHANGED", "NEW -> SHORTLISTED");

        assertThat(event.getId()).isEqualTo(6L);
        assertThat(event.getEntityId()).isEqualTo(3L);
        assertThat(event.getAction()).isEqualTo("STATUS_CHANGED");
        assertThat(event.getDetails()).isEqualTo("NEW -> SHORTLISTED");
        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event).isEqualTo(twin).hasSameHashCodeAs(twin).isNotEqualTo(new AuditEvent());

        AuditEvent preset = new AuditEvent();
        preset.getTimestamp();
        preset.setTimestamp(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        preset.stamp();
        assertThat(preset.getTimestamp()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
