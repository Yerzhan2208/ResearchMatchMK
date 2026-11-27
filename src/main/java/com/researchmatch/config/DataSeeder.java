package com.researchmatch.config;

import com.researchmatch.factory.OpportunityBuilder;
import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.FacultyRepository;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.StudentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds the deterministic demo dataset when the database is empty (Member 3).
 *
 * <p>Content: three CityUHK Computer Science faculty members, six research opportunities and four sample
 * students. Runs on every application start; idempotent because it exits early when opportunities already
 * exist, so the test database is never re-seeded and is never assumed empty.
 *
 * <p><strong>Privacy:</strong> the sample students are seeded as <em>not</em> searchable by faculty — the
 * demo starts with an empty candidate list and a student opts in explicitly.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    /** Contact e-mail the demo role switcher reuses for its own demo student. */
    static final String DEMO_STUDENT_EMAIL = "demo.student@cityu.edu.hk";

    private final FacultyRepository facultyRepository;
    private final OpportunityRepository opportunityRepository;
    private final StudentRepository studentRepository;

    public DataSeeder(FacultyRepository facultyRepository,
                      OpportunityRepository opportunityRepository,
                      StudentRepository studentRepository) {
        this.facultyRepository = facultyRepository;
        this.opportunityRepository = opportunityRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    public void run(String... args) {
        if (opportunityRepository.count() > 0) {
            return;
        }
        seedFacultiesAndOpportunities();
        seedStudents();
    }

    private void seedFacultiesAndOpportunities() {
        // OpenAlex ids are OURS, not real: OpenAlex assigns ids in the real A5xxxxxxxxx space and this
        // fictional faculty member has no real record to point at. A2RRCMK3ZDBF7 matches the offline
        // fixture in OpenAlexPublicationAdapter, so "Import from OpenAlex" works on a fresh checkout with
        // no API key; the app never calls OpenAlex to verify it.
        FacultyProfile chan = facultyRepository.save(faculty(
                "Prof. Wei Chan", "Computer Science", "A2RRCMK3ZDBF7",
                "Natural Language Processing", "Machine Learning"));
        FacultyProfile wong = facultyRepository.save(faculty(
                "Dr. Ka-Yan Wong", "Computer Science", null,
                "Data Mining", "Databases"));
        FacultyProfile lam = facultyRepository.save(faculty(
                "Dr. Ho-Yan Lam", "Computer Science", null,
                "Computer Vision", "Human-Computer Interaction"));

        List<Opportunity> opportunities = List.of(
                new OpportunityBuilder()
                        .facultyId(chan.getId())
                        .title("NLP for Course Feedback at CityUHK")
                        .description("Mine open-ended teaching-evaluation comments to surface recurring "
                                + "themes and sentiment, so lecturers can act on feedback quickly.")
                        .requiredSkills(Set.of("Python"))
                        .preferredSkills(Set.of("NLP", "Pandas"))
                        .hoursPerWeek(8)
                        .compensationType("CREDIT")
                        .beginnerFriendly(true)
                        .maxApplicants(3)
                        .minAcademicYear(1)
                        .build(),
                new OpportunityBuilder()
                        .facultyId(chan.getId())
                        .title("Transformer Models for Cantonese Text")
                        .description("Adapt an open multilingual model to Cantonese, which is under-represented "
                                + "in most training corpora.")
                        .requiredSkills(Set.of("Python", "PyTorch"))
                        .preferredSkills(Set.of("NLP", "Machine Learning"))
                        .hoursPerWeek(12)
                        .compensationType("PAID")
                        .beginnerFriendly(false)
                        .maxApplicants(2)
                        .minAcademicYear(3)
                        .build(),
                new OpportunityBuilder()
                        .facultyId(wong.getId())
                        .title("Mining Student Performance Records")
                        .description("Build reproducible analyses over anonymised academic records to find "
                                + "early-warning signals for struggling students.")
                        .requiredSkills(Set.of("SQL"))
                        .preferredSkills(Set.of("Python", "Pandas"))
                        .hoursPerWeek(10)
                        .compensationType("CREDIT")
                        .beginnerFriendly(true)
                        .maxApplicants(4)
                        .minAcademicYear(2)
                        .build(),
                new OpportunityBuilder()
                        .facultyId(wong.getId())
                        .title("Query Optimisation for Campus Databases")
                        .description("Profile slow reports from campus systems and propose indexes and rewrites "
                                + "that measurably reduce run time.")
                        .requiredSkills(Set.of("SQL", "Java"))
                        .preferredSkills(Set.of("Databases"))
                        .hoursPerWeek(6)
                        .compensationType("VOLUNTEER")
                        .beginnerFriendly(false)
                        .maxApplicants(2)
                        .minAcademicYear(3)
                        .build(),
                new OpportunityBuilder()
                        .facultyId(lam.getId())
                        .title("Vision-Based Attendance for Smart Classrooms")
                        .description("Prototype privacy-preserving occupancy counting that never stores "
                                + "identifiable images.")
                        .requiredSkills(Set.of("Python", "OpenCV"))
                        .preferredSkills(Set.of("Computer Vision"))
                        .hoursPerWeek(10)
                        .compensationType("PAID")
                        .beginnerFriendly(false)
                        .maxApplicants(3)
                        .minAcademicYear(2)
                        .build(),
                new OpportunityBuilder()
                        .facultyId(lam.getId())
                        .title("Usability Study of Student Portals")
                        .description("Run moderated usability sessions on the student portal and turn findings "
                                + "into concrete interface recommendations.")
                        .requiredSkills(Set.of("Figma"))
                        .preferredSkills(Set.of("Human-Computer Interaction"))
                        .hoursPerWeek(5)
                        .compensationType("VOLUNTEER")
                        .beginnerFriendly(true)
                        .maxApplicants(5)
                        .minAcademicYear(1)
                        .build());
        opportunityRepository.saveAll(opportunities);
    }

    private void seedStudents() {
        studentRepository.saveAll(List.of(
                student("Mei-Ling Ho", "mei-ling.ho@my.cityu.edu.hk", "Computer Science", 3, 12,
                        Set.of("NLP", "Data Mining"), Set.of("Python", "SQL"), "demo-mei-ling-ho"),
                student("Arjun Patel", "arjun.patel@my.cityu.edu.hk", "Data Science", 2, 10,
                        Set.of("Machine Learning"), Set.of("Python", "Pandas"), "demo-arjun-patel"),
                student("Sze-Wing Lau", "szewing.lau@my.cityu.edu.hk", "Computer Science", 4, 8,
                        Set.of("Computer Vision", "Databases"), Set.of("Java", "OpenCV"), "demo-sze-wing-lau"),
                student("Daniel Chan", "daniel.chan@my.cityu.edu.hk", "Information Engineering", 1, 6,
                        Set.of("Human-Computer Interaction"), Set.of("Figma"), null)));
    }

    private static FacultyProfile faculty(String name, String department, String openAlexId,
                                          String... researchAreas) {
        return new FacultyProfile(null, name, department,
                new LinkedHashSet<>(List.of(researchAreas)), openAlexId);
    }

    private static StudentProfile student(String name, String email, String major, int academicYear,
                                          int hoursPerWeek, Set<String> interests, Set<String> claimedSkills,
                                          String githubUsername) {
        StudentProfile student = new StudentProfile(null, name, email, major, academicYear, hoursPerWeek,
                githubUsername);
        student.setInterests(new LinkedHashSet<>(interests));
        student.setClaimedSkills(new LinkedHashSet<>(claimedSkills));
        // Privacy rule: sample data never opts a student in; only the student may do that.
        student.setSearchableByFaculty(false);
        return student;
    }
}
