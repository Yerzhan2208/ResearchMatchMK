package com.researchmatch.controllers;

import com.researchmatch.models.Application;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;

/**
 * Read-only view model for one card on the professor's pipeline board.
 *
 * <p>{@link Application} stores only {@code studentId} and {@code opportunityId} as foreign keys, so a template
 * holding a bare application can show an id but never a name or a job title. This record carries the resolved
 * entities alongside the application so the board can render who applied, to what, and what they wrote.
 *
 * <p>{@code student} and {@code opportunity} are nullable: an application whose student or opportunity row
 * has been removed still appears on the board rather than disappearing, and the template falls back to the id.
 */
public record ApplicationCard(Application application, StudentProfile student, Opportunity opportunity) {

    /** The student's display name, or {@code "Student <id>"} when the profile is missing. */
    public String studentName() {
        if (student != null && student.getName() != null && !student.getName().isBlank()) {
            return student.getName();
        }
        return "Student " + application.getStudentId();
    }

    /** The opportunity title, or {@code "Opportunity <id>"} when the row is missing. */
    public String opportunityTitle() {
        if (opportunity != null && opportunity.getTitle() != null && !opportunity.getTitle().isBlank()) {
            return opportunity.getTitle();
        }
        return "Opportunity " + application.getOpportunityId();
    }

    /** The student's stated motivation, or an empty string so the template need not null-check. */
    public String statement() {
        return application.getStatement() == null ? "" : application.getStatement();
    }
}
