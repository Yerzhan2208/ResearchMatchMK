package com.researchmatch.matching;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Final link: turns the notes gathered so far into a human-readable summary and preparation advice.
 *
 * <p>This layer never changes scores or eligibility — it only writes explanation notes and
 * {@link MatchContext#setRecommendedPrep(String)}. The summary is assembled from the pair's eligibility and
 * its matched / missing skills, and is guaranteed non-blank for any evaluated pair.
 */
@Component
public class ExplanationHandler extends AbstractMatchLayerHandler {

    public static final String LAYER = "explanation";

    @Override
    public String layerName() {
        return LAYER;
    }

    @Override
    protected MatchLayerHandler.LayerResult apply(MatchContext context) {
        Set<String> matched = sanitise(context.getMatchedSkills()).stream()
                .map(ExplanationHandler::upper).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> missing = sanitise(context.getMissingSkills()).stream()
                .map(ExplanationHandler::upper).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        context.addExplanationNote(buildSummary(context.isEligible(), matched, missing));
        context.setRecommendedPrep(buildPrep(context.isEligible(), matched, missing));

        return MatchLayerHandler.LayerResult.CONTINUE;
    }

    // ---------------------------------------------------------------- summary

    private static String buildSummary(boolean eligible, Set<String> matched, Set<String> missing) {
        if (!eligible) {
            // The rejection reason is already recorded as a note by the eligibility layer.
            return "This opportunity is not a match for your current profile.";
        }

        StringBuilder sb = new StringBuilder("You are eligible for this opportunity.");
        if (!matched.isEmpty()) {
            sb.append(" Your strengths match: ").append(join(matched)).append('.');
        }
        if (!missing.isEmpty()) {
            sb.append(" Skills still missing: ").append(join(missing)).append('.');
        }
        if (matched.isEmpty() && missing.isEmpty()) {
            sb.append(" This opportunity has no specific skill requirements.");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- preparation advice

    private static String buildPrep(boolean eligible, Set<String> matched, Set<String> missing) {
        if (!eligible) {
            return "Review the eligibility requirements above before applying to this opportunity.";
        }
        if (!missing.isEmpty()) {
            return "Prepare by strengthening these required skills: " + join(missing) + '.';
        }
        if (!matched.isEmpty()) {
            return "You meet all listed skill requirements; prepare to discuss your work in "
                    + join(matched) + '.';
        }
        return "No specific preparation is required; review the opportunity description and contact the faculty.";
    }

    // ---------------------------------------------------------------- helpers

    private static String join(Set<String> skills) {
        return String.join(", ", new ArrayList<>(skills));
    }

    private static Set<String> sanitise(Set<String> skills) {
        Set<String> result = new LinkedHashSet<>();
        if (skills != null) {
            for (String skill : skills) {
                if (skill != null && !skill.isBlank()) {
                    result.add(skill.trim());
                }
            }
        }
        return result;
    }

    private static String upper(String value) {
        return value.toUpperCase(java.util.Locale.ROOT);
    }
}
