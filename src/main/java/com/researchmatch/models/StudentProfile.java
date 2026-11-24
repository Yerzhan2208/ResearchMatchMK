package com.researchmatch.models;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A student looking for a research opportunity.
 *
 * <p>PRIVACY RULE: {@link #searchableByFaculty} MUST default to {@code false}.
 */
@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String major;

    /** Academic year of study, e.g. 3. */
    private Integer academicYear;

    private Integer hoursPerWeek;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "student_interests", joinColumns = @JoinColumn(name = "student_id"))
    @Column(name = "interest")
    private Set<String> interests = new LinkedHashSet<>();

    /** Skills the student says they have (unverified). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "student_claimed_skills", joinColumns = @JoinColumn(name = "student_id"))
    @Column(name = "skill")
    private Set<String> claimedSkills = new LinkedHashSet<>();

    /** Skills backed by evidence (e.g. GitHub repositories). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "student_verified_skills", joinColumns = @JoinColumn(name = "student_id"))
    @Column(name = "skill")
    private Set<String> verifiedSkills = new LinkedHashSet<>();

    private String githubUsername;

    /** PRIVACY RULE: opt-in only. Never change this default to true. */
    @Column(nullable = false)
    private boolean searchableByFaculty = false;

    public StudentProfile() {
    }

    public StudentProfile(Long id, String name, String email, String major, Integer academicYear,
                          Integer hoursPerWeek, String githubUsername) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.major = major;
        this.academicYear = academicYear;
        this.hoursPerWeek = hoursPerWeek;
        this.githubUsername = githubUsername;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public Integer getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(Integer academicYear) {
        this.academicYear = academicYear;
    }

    public Integer getHoursPerWeek() {
        return hoursPerWeek;
    }

    public void setHoursPerWeek(Integer hoursPerWeek) {
        this.hoursPerWeek = hoursPerWeek;
    }

    public Set<String> getInterests() {
        return interests;
    }

    public void setInterests(Set<String> interests) {
        this.interests = interests;
    }

    public Set<String> getClaimedSkills() {
        return claimedSkills;
    }

    public void setClaimedSkills(Set<String> claimedSkills) {
        this.claimedSkills = claimedSkills;
    }

    public Set<String> getVerifiedSkills() {
        return verifiedSkills;
    }

    public void setVerifiedSkills(Set<String> verifiedSkills) {
        this.verifiedSkills = verifiedSkills;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public boolean isSearchableByFaculty() {
        return searchableByFaculty;
    }

    public void setSearchableByFaculty(boolean searchableByFaculty) {
        this.searchableByFaculty = searchableByFaculty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // Unsaved (id == null) entities are never equal to another instance.
        if (id == null || !(o instanceof StudentProfile other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
