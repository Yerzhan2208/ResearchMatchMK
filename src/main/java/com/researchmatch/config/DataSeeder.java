package com.researchmatch.config;

import com.researchmatch.factory.OpportunityBuilder;
import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.Opportunity;
import com.researchmatch.repository.FacultyRepository;
import com.researchmatch.repository.OpportunityRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds a small, deterministic demo dataset when the database is empty (Member 3).
 *
 * <p>Runs on every application start; idempotent because it exits early when opportunities already exist.
 * TODO (Member 3): replace the sample data with the agreed demo scenario.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final FacultyRepository facultyRepository;
    private final OpportunityRepository opportunityRepository;

    public DataSeeder(FacultyRepository facultyRepository, OpportunityRepository opportunityRepository) {
        this.facultyRepository = facultyRepository;
        this.opportunityRepository = opportunityRepository;
    }

    @Override
    public void run(String... args) {
        if (opportunityRepository.count() > 0) {
            return;
        }
        FacultyProfile faculty = facultyRepository.save(new FacultyProfile(
                null, "Dr. Ada Lee", "Computer Science",
                new LinkedHashSet<>(Set.of("Natural Language Processing", "Data Mining")), "A0000000001"));

        List<Opportunity> opportunities = List.of(
                new OpportunityBuilder()
                        .facultyId(faculty.getId())
                        .title("NLP for course feedback")
                        .description("Analyse open-ended course comments with lightweight NLP.")
                        .requiredSkills(Set.of("Java", "Python"))
                        .preferredSkills(Set.of("NLP"))
                        .hoursPerWeek(8)
                        .compensationType("CREDIT")
                        .beginnerFriendly(true)
                        .maxApplicants(3)
                        .build(),
                new OpportunityBuilder()
                        .facultyId(faculty.getId())
                        .title("Mining student performance data")
                        .description("Build reproducible analyses over anonymised academic records.")
                        .requiredSkills(Set.of("Python", "SQL"))
                        .preferredSkills(Set.of("Pandas"))
                        .hoursPerWeek(10)
                        .compensationType("PAID")
                        .beginnerFriendly(false)
                        .maxApplicants(2)
                        .build());
        opportunityRepository.saveAll(opportunities);
    }
}
