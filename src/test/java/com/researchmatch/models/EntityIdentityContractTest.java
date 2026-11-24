package com.researchmatch.models;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every entity uses the same identity contract: equal to itself, equal to a same-id instance, never equal
 * to null, to a different type, or to a different id, and transient (id-less) instances are never equal.
 * This drives every branch of the {@code equals} implementations in one place.
 */
class EntityIdentityContractTest {

    record Case(String name, Object entity, Object sameId, Object differentId, Object unsaved) {
    }

    private static final List<Case> CASES = List.of(
            new Case("StudentProfile",
                    new StudentProfile(1L, "a", "a@x", "CS", 1, 1, "gh"),
                    new StudentProfile(1L, "b", "b@x", "EE", 2, 2, "gh2"),
                    new StudentProfile(2L, "a", "a@x", "CS", 4, 1, "gh"),
                    new StudentProfile()),
            new Case("FacultyProfile",
                    new FacultyProfile(1L, "a", "CS", null, "o"),
                    new FacultyProfile(1L, "b", "EE", null, "p"),
                    new FacultyProfile(2L, "a", "CS", null, "o"),
                    new FacultyProfile()),
            new Case("Opportunity",
                    new Opportunity(1L, 1L, "t", "d", 2),
                    new Opportunity(1L, 2L, "t2", "d2", 3),
                    new Opportunity(2L, 1L, "t", "d", 2),
                    new Opportunity()),
            new Case("Application",
                    new Application(1L, 1L, 1L),
                    new Application(1L, 2L, 2L),
                    new Application(2L, 1L, 1L),
                    new Application()),
            new Case("SkillEvidence",
                    new SkillEvidence(1L, 1L, "Java", "r", "s"),
                    new SkillEvidence(1L, 2L, "Go", "r2", "s2"),
                    new SkillEvidence(2L, 1L, "Java", "r", "s"),
                    new SkillEvidence()),
            new Case("MatchResult",
                    new MatchResult(1L, 1L, 1L),
                    new MatchResult(1L, 2L, 2L),
                    new MatchResult(2L, 1L, 1L),
                    new MatchResult()),
            new Case("AuditEvent",
                    new AuditEvent(1L, 1L, "A", "d"),
                    new AuditEvent(1L, 2L, "B", "e"),
                    new AuditEvent(2L, 1L, "A", "d"),
                    new AuditEvent()));

    @Test
    void everyEntityFollowsTheIdentityContract() {
        for (Case c : CASES) {
            assertThat(c.entity().equals(c.entity())).as("%s is equal to itself", c.name()).isTrue();
            assertThat(c.entity()).as("%s same id", c.name()).isEqualTo(c.sameId()).hasSameHashCodeAs(c.sameId());
            assertThat(c.entity()).as("%s different id", c.name()).isNotEqualTo(c.differentId());
            assertThat(c.entity().equals(null)).as("%s not equal to null", c.name()).isFalse();
            assertThat(c.entity().equals("nope")).as("%s not equal to another type", c.name()).isFalse();
            assertThat(c.unsaved()).as("%s without id is equal to itself only", c.name())
                    .hasSameHashCodeAs(c.unsaved())
                    .isNotEqualTo(new StudentProfile())
                    .isNotEqualTo(new Object());
        }
    }

    @Test
    void everyDomainEntityIsCovered() {
        assertThat(CASES).extracting(Case::name).containsExactly(
                "StudentProfile", "FacultyProfile", "Opportunity", "Application",
                "SkillEvidence", "MatchResult", "AuditEvent");
    }
}
