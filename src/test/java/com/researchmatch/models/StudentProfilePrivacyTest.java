package com.researchmatch.models;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProfilePrivacyTest {

    @Test
    void searchableByFacultyDefaultsToFalse() {
        assertThat(new StudentProfile().isSearchableByFaculty()).isFalse();
        assertThat(new StudentProfile(1L, "Ann", "ann@cityu.edu.hk", "CS", 3, 10, "ann").isSearchableByFaculty())
                .isFalse();
    }

    @Test
    void canOptInAndRoundTripCollections() {
        StudentProfile profile = new StudentProfile();
        Set<String> skills = new LinkedHashSet<>(Set.of("Java", "SQL"));
        profile.setClaimedSkills(skills);
        profile.setVerifiedSkills(skills);
        profile.setInterests(Set.of("NLP"));
        profile.setSearchableByFaculty(true);

        assertThat(profile.getClaimedSkills()).containsExactlyInAnyOrder("Java", "SQL");
        assertThat(profile.getVerifiedSkills()).containsExactlyInAnyOrder("Java", "SQL");
        assertThat(profile.getInterests()).containsExactly("NLP");
        assertThat(profile.isSearchableByFaculty()).isTrue();
    }
}
