package com.researchmatch.factory;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildersTest {

    @Test
    void matchResultBuilderPopulatesEveryField() {
        MatchResult result = new MatchResultBuilder()
                .studentId(1L)
                .opportunityId(2L)
                .totalScore(72.5)
                .eligible(true)
                .matchedSkills(List.of("Java", "SQL"))
                .missingSkills(List.of("Python"))
                .explanationSummary("Strong backend fit")
                .recommendedPrep("Learn pandas")
                .build();

        assertThat(result.getStudentId()).isEqualTo(1L);
        assertThat(result.getOpportunityId()).isEqualTo(2L);
        assertThat(result.getTotalScore()).isEqualTo(72.5);
        assertThat(result.isEligible()).isTrue();
        assertThat(result.getMatchedSkills()).containsExactly("Java", "SQL");
        assertThat(result.getMissingSkills()).containsExactly("Python");
        assertThat(result.getExplanationSummary()).isEqualTo("Strong backend fit");
        assertThat(result.getRecommendedPrep()).isEqualTo("Learn pandas");
    }

    @Test
    void matchResultBuilderHandlesNullCollections() {
        MatchResult result = new MatchResultBuilder().matchedSkills(null).missingSkills(null).build();

        assertThat(result.getMatchedSkills()).isNotNull().isEmpty();
        assertThat(result.getMissingSkills()).isNotNull().isEmpty();
    }

    @Test
    void matchResultBuilderCopiesInputs() {
        java.util.List<String> source = new java.util.ArrayList<>(List.of("Java"));
        MatchResult result = new MatchResultBuilder().matchedSkills(source).build();
        source.add("Rust");

        assertThat(result.getMatchedSkills()).containsExactly("Java");
    }

    @Test
    void opportunityBuilderPopulatesEveryField() {
        Opportunity opportunity = new OpportunityBuilder()
                .facultyId(9L)
                .title("NLP project")
                .description("Work on transformers")
                .requiredSkills(Set.of("Java"))
                .preferredSkills(Set.of("Python"))
                .hoursPerWeek(10)
                .compensationType("CREDIT")
                .beginnerFriendly(true)
                .maxApplicants(2)
                .build();

        assertThat(opportunity.getFacultyId()).isEqualTo(9L);
        assertThat(opportunity.getTitle()).isEqualTo("NLP project");
        assertThat(opportunity.getDescription()).isEqualTo("Work on transformers");
        assertThat(opportunity.getRequiredSkills()).containsExactly("Java");
        assertThat(opportunity.getPreferredSkills()).containsExactly("Python");
        assertThat(opportunity.getHoursPerWeek()).isEqualTo(10);
        assertThat(opportunity.getCompensationType()).isEqualTo("CREDIT");
        assertThat(opportunity.isBeginnerFriendly()).isTrue();
        assertThat(opportunity.getMaxApplicants()).isEqualTo(2);
    }

    @Test
    void opportunityBuilderDefaultsCollectionsAndRejectsIncompleteInput() {
        Opportunity minimal = new OpportunityBuilder().facultyId(1L).title("t").requiredSkills(null)
                .preferredSkills(null).build();
        assertThat(minimal.getRequiredSkills()).isNotNull().isEmpty();
        assertThat(minimal.getPreferredSkills()).isNotNull().isEmpty();

        assertThatThrownBy(() -> new OpportunityBuilder().title("t").build())
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("faculty");
        assertThatThrownBy(() -> new OpportunityBuilder().facultyId(1L).title("  ").build())
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> new OpportunityBuilder().facultyId(1L)
                .title(null).build())
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> new OpportunityBuilder().facultyId(1L).title("t").hoursPerWeek(0).build())
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("hoursPerWeek");
        assertThatThrownBy(() -> new OpportunityBuilder().facultyId(1L).title("t").maxApplicants(-1).build())
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("maxApplicants");
    }
}
