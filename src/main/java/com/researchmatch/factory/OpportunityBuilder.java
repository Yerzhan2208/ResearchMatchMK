package com.researchmatch.factory;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.models.Opportunity;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Builder for {@link Opportunity} (Member 3).
 *
 * <p>Consumers: {@code ResearchMatchFacade} (Member 4) when faculty publish opportunities.
 * Required fields are checked in {@link #build()}.
 */
public class OpportunityBuilder {

    private final Opportunity opportunity = new Opportunity();

    public OpportunityBuilder facultyId(Long facultyId) {
        opportunity.setFacultyId(facultyId);
        return this;
    }

    public OpportunityBuilder title(String title) {
        opportunity.setTitle(title);
        return this;
    }

    public OpportunityBuilder description(String description) {
        opportunity.setDescription(description);
        return this;
    }

    public OpportunityBuilder requiredSkills(Collection<String> skills) {
        opportunity.setRequiredSkills(copyOf(skills));
        return this;
    }

    /** Defensive-copy helper shared by the collection setters. */
    private static LinkedHashSet<String> copyOf(Collection<String> skills) {
        return skills == null ? new LinkedHashSet<>() : new LinkedHashSet<>(skills);
    }

    public OpportunityBuilder preferredSkills(Collection<String> skills) {
        opportunity.setPreferredSkills(copyOf(skills));
        return this;
    }

    public OpportunityBuilder hoursPerWeek(Integer hoursPerWeek) {
        opportunity.setHoursPerWeek(hoursPerWeek);
        return this;
    }

    public OpportunityBuilder compensationType(String compensationType) {
        opportunity.setCompensationType(compensationType);
        return this;
    }

    public OpportunityBuilder beginnerFriendly(boolean beginnerFriendly) {
        opportunity.setBeginnerFriendly(beginnerFriendly);
        return this;
    }

    public OpportunityBuilder maxApplicants(Integer maxApplicants) {
        opportunity.setMaxApplicants(maxApplicants);
        return this;
    }

    /**
     * @throws InvalidRequestException if {@code facultyId} is null, {@code title} is blank, or
     *                                 {@code hoursPerWeek} is present but not positive
     */
    public Opportunity build() {
        if (opportunity.getFacultyId() == null) {
            throw new InvalidRequestException("Opportunity requires a faculty id");
        }
        if (opportunity.getTitle() == null || opportunity.getTitle().isBlank()) {
            throw new InvalidRequestException("Opportunity requires a title");
        }
        Integer hours = opportunity.getHoursPerWeek();
        if (hours != null && hours <= 0) {
            throw new InvalidRequestException("hoursPerWeek must be positive");
        }
        Integer max = opportunity.getMaxApplicants();
        if (max != null && max <= 0) {
            throw new InvalidRequestException("maxApplicants must be positive");
        }
        return opportunity;
    }
}
