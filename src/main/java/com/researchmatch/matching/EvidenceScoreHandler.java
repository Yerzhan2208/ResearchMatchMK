package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the student's skills against the opportunity's skill requirements into matched/missing skills and
 * adds an evidence bonus for skills that are backed by verified evidence.
 *
 * <p>Skill semantics (per the project contracts): a skill is <em>matched</em> when the student has it as
 * either a claimed or a verified skill, otherwise it is <em>missing</em>. Only verified skills (see
 * {@link MatchContext#verifiedSkillSet()}) earn the evidence bonus; claimed-only overlaps are counted as
 * matched but earn no bonus. Skill comparison is case-insensitive and ignores surrounding whitespace.
 *
 * <p>The bonus is {@link #BONUS_PER_VERIFIED_SKILL} per verified matched skill, capped at
 * {@link #MAX_BONUS}, and recorded under {@link #LAYER} via {@link MatchContext#addScore(String, double)}.
 */
@Component
public class EvidenceScoreHandler extends AbstractMatchLayerHandler {

    public static final String LAYER = "evidence";

    /** Points awarded for each matched skill that is backed by verified evidence. */
    static final double BONUS_PER_VERIFIED_SKILL = 2.0;

    /** Upper bound so a very broad skill set cannot dominate the overall score. */
    static final double MAX_BONUS = 10.0;

    @Override
    public String layerName() {
        return LAYER;
    }

    @Override
    protected MatchLayerHandler.LayerResult apply(MatchContext context) {
        StudentProfile student = context.getStudent();
        Opportunity opportunity = context.getOpportunity();

        Set<String> verified = normalise(context.verifiedSkillSet());
        Set<String> claimed = normalise(student.getClaimedSkills());
        Set<String> required = normalise(opportunity.getRequiredSkills());
        Set<String> preferred = normalise(opportunity.getPreferredSkills());

        Set<String> matched = new LinkedHashSet<>();
        Set<String> missing = new LinkedHashSet<>();

        for (String skill : required) {
            if (verified.contains(skill) || claimed.contains(skill)) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }
        for (String skill : preferred) {
            if (verified.contains(skill) || claimed.contains(skill)) {
                matched.add(skill);
            }
        }

        context.addMatchedSkills(matched);
        context.addMissingSkills(missing);

        long verifiedMatches = matched.stream().filter(verified::contains).count();
        double bonus = Math.min(verifiedMatches * BONUS_PER_VERIFIED_SKILL, MAX_BONUS);
        context.addScore(layerName(), bonus);

        return MatchLayerHandler.LayerResult.CONTINUE;
    }

    private static Set<String> normalise(Set<String> skills) {
        Set<String> result = new LinkedHashSet<>();
        if (skills != null) {
            for (String skill : skills) {
                if (skill != null && !skill.isBlank()) {
                    result.add(skill.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }
}
