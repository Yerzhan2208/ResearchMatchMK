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

    public Opportunity() {
    }

    public Opportunity(Long id, Long facultyId, String title, String description) {
        this.id = id;
        this.facultyId = facultyId;
        this.title = title;
        this.description = description;
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
