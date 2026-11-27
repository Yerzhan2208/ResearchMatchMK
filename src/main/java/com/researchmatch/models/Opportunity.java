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

/** A research opportunity posted by a faculty member. */
@Entity
@Table(name = "opportunities")
public class Opportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long facultyId;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "opportunity_required_skills", joinColumns = @JoinColumn(name = "opportunity_id"))
    @Column(name = "skill")
    private Set<String> requiredSkills = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "opportunity_preferred_skills", joinColumns = @JoinColumn(name = "opportunity_id"))
    @Column(name = "skill")
    private Set<String> preferredSkills = new LinkedHashSet<>();

    private Integer hoursPerWeek;

    /** e.g. "VOLUNTEER", "CREDIT", "PAID". */
    private String compensationType;

    private boolean beginnerFriendly;

    private Integer maxApplicants;

    /**
     * Minimum academic year a candidate must have reached, e.g. {@code 1} = open to all undergraduates,
     * {@code 2} = second-year and above. Defaults to {@code 1} so existing callers stay eligible-compatible.
     */
    private Integer minAcademicYear = 1;

    public Opportunity() {
    }

    public Opportunity(Long id, Long facultyId, String title, String description) {
        this(id, facultyId, title, description, 1);
    }

    /**
     * Overload that also sets the minimum academic year.
     *
     * @param minAcademicYear required student academic year; null is treated as {@code 1}
     */
    public Opportunity(Long id, Long facultyId, String title, String description, Integer minAcademicYear) {
        this.id = id;
        this.facultyId = facultyId;
        this.title = title;
        this.description = description;
        this.minAcademicYear = minAcademicYear == null ? 1 : minAcademicYear;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(Long facultyId) {
        this.facultyId = facultyId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(Set<String> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public Set<String> getPreferredSkills() {
        return preferredSkills;
    }

    public void setPreferredSkills(Set<String> preferredSkills) {
        this.preferredSkills = preferredSkills;
    }

    public Integer getHoursPerWeek() {
        return hoursPerWeek;
    }

    public void setHoursPerWeek(Integer hoursPerWeek) {
        this.hoursPerWeek = hoursPerWeek;
    }

    public String getCompensationType() {
        return compensationType;
    }

    public void setCompensationType(String compensationType) {
        this.compensationType = compensationType;
    }

    public boolean isBeginnerFriendly() {
        return beginnerFriendly;
    }

    public void setBeginnerFriendly(boolean beginnerFriendly) {
        this.beginnerFriendly = beginnerFriendly;
    }

    public Integer getMaxApplicants() {
        return maxApplicants;
    }

    public void setMaxApplicants(Integer maxApplicants) {
        this.maxApplicants = maxApplicants;
    }

    public Integer getMinAcademicYear() {
        return minAcademicYear;
    }

    /** null is normalised to {@code 1} (open to all undergraduates). */
    public void setMinAcademicYear(Integer minAcademicYear) {
        this.minAcademicYear = minAcademicYear == null ? 1 : minAcademicYear;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // Unsaved (id == null) entities are never equal to another instance.
        if (id == null || !(o instanceof Opportunity other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
