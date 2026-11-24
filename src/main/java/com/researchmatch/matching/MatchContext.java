package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mutable working state carried through the matching {@link MatchLayerHandler} chain.
 *
 * <p>One instance per (student, opportunity) evaluation. Handlers read the two entities, then write
 * scores / skills / flags. All collections are non-null and insertion-ordered; nothing here is thread-safe,
 * and a context must not be shared between threads.
 *
 * <p>Contract for every handler: never mutate the {@link #getStudent() student} or
 * {@link #getOpportunity() opportunity} entities, only this context.
 */
public final class MatchContext {

    private final StudentProfile student;
    private final Opportunity opportunity;

    private final Set<String> matchedSkills = new LinkedHashSet<>();
    private final Set<String> missingSkills = new LinkedHashSet<>();
    private final List<String> explanationNotes = new ArrayList<>();
    private final Map<String, Double> layerScores = new LinkedHashMap<>();

    private double totalScore;
    private boolean eligible = true;
    private String recommendedPrep;

    public MatchContext(StudentProfile student, Opportunity opportunity) {
        this.student = Objects.requireNonNull(student, "student must not be null");
        this.opportunity = Objects.requireNonNull(opportunity, "opportunity must not be null");
    }

    public StudentProfile getStudent() {
        return student;
    }

    public Opportunity getOpportunity() {
        return opportunity;
    }

    /**
     * Marks the pair as ineligible and records why. The chain short-circuits on ineligibility, so this is
     * terminal: later handlers must not be relied upon afterwards.
     */
    public void markIneligible(String reason) {
        this.eligible = false;
        addExplanationNote(reason);
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public void addScore(String layerName, double score) {
        layerScores.put(layerName, score);
        totalScore += score;
    }

    public Map<String, Double> getLayerScores() {
        return layerScores;
    }

    public Set<String> getMatchedSkills() {
        return matchedSkills;
    }

    public Set<String> getMissingSkills() {
        return missingSkills;
    }

    public void addMatchedSkills(Collection<String> skills) {
        if (skills != null) {
            skills.stream().filter(Objects::nonNull).forEach(matchedSkills::add);
        }
    }

    public void addMissingSkills(Collection<String> skills) {
        if (skills != null) {
            skills.stream().filter(Objects::nonNull).forEach(missingSkills::add);
        }
    }

    public List<String> getExplanationNotes() {
        return explanationNotes;
    }

    public void addExplanationNote(String note) {
        if (note != null && !note.isBlank()) {
            explanationNotes.add(note);
        }
    }

    /** Joined explanation notes, or an empty string when no handler produced one yet. */
    public String getExplanationSummary() {
        return String.join(" ", explanationNotes);
    }

    public String getRecommendedPrep() {
        return recommendedPrep;
    }

    public void setRecommendedPrep(String recommendedPrep) {
        this.recommendedPrep = recommendedPrep;
    }

    /** Skills the student can back with evidence (verified skills). */
    public Set<String> verifiedSkillSet() {
        return student.getVerifiedSkills().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
