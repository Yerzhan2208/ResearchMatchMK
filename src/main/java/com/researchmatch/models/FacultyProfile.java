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

/** A faculty member who posts research opportunities. */
@Entity
@Table(name = "faculty_profiles")
public class FacultyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String department;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "faculty_research_areas", joinColumns = @JoinColumn(name = "faculty_id"))
    @Column(name = "research_area")
    private Set<String> researchAreas = new LinkedHashSet<>();

    /** Identifier used by the OpenAlex adapter; may be blank if the faculty is not linked. */
    private String openAlexId;

    public FacultyProfile() {
    }

    public FacultyProfile(Long id, String name, String department, Set<String> researchAreas, String openAlexId) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.researchAreas = researchAreas;
        this.openAlexId = openAlexId;
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

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Set<String> getResearchAreas() {
        return researchAreas;
    }

    public void setResearchAreas(Set<String> researchAreas) {
        this.researchAreas = researchAreas;
    }

    public String getOpenAlexId() {
        return openAlexId;
    }

    public void setOpenAlexId(String openAlexId) {
        this.openAlexId = openAlexId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // Unsaved (id == null) entities are never equal to another instance.
        if (id == null || !(o instanceof FacultyProfile other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
