package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * First link: hard eligibility rules (academic year, hours available, required skills, ...).
 *
 * <p>MUST reject when {@code opportunity.getMinAcademicYear() != null &&
 * (student.getAcademicYear() == null || student.getAcademicYear() < opportunity.getMinAcademicYear())}.
 *
 * <p>On failure calls {@link MatchContext#markIneligible(String)} and returns
 * {@link MatchLayerHandler.LayerResult#STOP}; otherwise returns {@code CONTINUE}.
 */
@Component
public class EligibilityFilterHandler extends AbstractMatchLayerHandler {

    public static final String LAYER = "eligibility";

    @Override
    public String layerName() {
        return LAYER;
    }

    @Override
    protected MatchLayerHandler.LayerResult apply(MatchContext context) {
        StudentProfile student = context.getStudent();
        Opportunity opportunity = context.getOpportunity();

        String email = student.getEmail();
        if (email == null || email.isBlank()) {
            return reject(context, "Student profile is missing an email address");
        }

        Integer studentHours = student.getHoursPerWeek();
        Integer requiredHours = opportunity.getHoursPerWeek();
        if (studentHours == null || studentHours <= 0) {
            return reject(context, "Student has no weekly availability");
        }
        if (requiredHours != null && requiredHours > 0 && studentHours < requiredHours) {
            return reject(context, "Student availability " + studentHours + "h/week is below the required "
                    + requiredHours + "h/week");
        }

        Integer minYear = opportunity.getMinAcademicYear();
        Integer studentYear = student.getAcademicYear();
        if (minYear != null && (studentYear == null || studentYear < minYear)) {
            return reject(context, "Requires academic year " + minYear + " or above (student year: "
                    + studentYear + ")");
        }

        if (!opportunity.isBeginnerFriendly()) {
            Set<String> requiredSkills = normalise(opportunity.getRequiredSkills());
            if (!requiredSkills.isEmpty()) {
                Set<String> studentSkills = new LinkedHashSet<>(normalise(student.getClaimedSkills()));
                studentSkills.addAll(normalise(student.getVerifiedSkills()));
                boolean overlaps = requiredSkills.stream().anyMatch(studentSkills::contains);
                if (!overlaps) {
                    return reject(context, "Student has none of the required skills: " + requiredSkills);
                }
            }
        }

        return MatchLayerHandler.LayerResult.CONTINUE;
    }

    private static MatchLayerHandler.LayerResult reject(MatchContext context, String reason) {
        context.markIneligible(reason);
        return MatchLayerHandler.LayerResult.STOP;
    }

    private static Set<String> normalise(Set<String> skills) {
        Set<String> result = new LinkedHashSet<>();
        if (skills != null) {
            for (String skill : skills) {
                if (skill != null && !skill.isBlank()) {
                    result.add(skill.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        return result;
    }
}
