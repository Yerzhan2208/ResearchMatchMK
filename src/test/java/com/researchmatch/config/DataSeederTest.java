package com.researchmatch.config;

import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.FacultyRepository;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.StudentRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Member 3 demo dataset. Tests live under {@code config/} because that is the package of
 * {@link DataSeeder}, mirroring the main-source layout.
 */
class DataSeederTest {

    /**
     * Runs against a fresh in-memory database per context, so the seeder really starts from empty.
     */
    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:h2:mem:seeder-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "spring.jpa.hibernate.ddl-auto=create-drop"
    })
    class SeededDatabase {

        @Autowired
        private DataSeeder dataSeeder;
        @Autowired
        private OpportunityRepository opportunityRepository;
        @Autowired
        private FacultyRepository facultyRepository;
        @Autowired
        private StudentRepository studentRepository;

        @Test
        void seedsCityUHKFacultyOpportunitiesAndStudents() {
            assertThat(facultyRepository.count()).isEqualTo(3);
            assertThat(opportunityRepository.count()).isEqualTo(6);
            assertThat(studentRepository.count()).isEqualTo(4);
        }

        @Test
        void seededFacultyCarryCityUHKResearchAreasAndOpenAlexIds() {
            List<FacultyProfile> faculties = facultyRepository.findAll();

            assertThat(faculties).isNotEmpty()
                    .allSatisfy(faculty -> {
                        assertThat(faculty.getDepartment()).isEqualTo("Computer Science");
                        assertThat(faculty.getResearchAreas()).isNotEmpty();
                    });
            assertThat(faculties).anySatisfy(
                    faculty -> assertThat(faculty.getOpenAlexId()).startsWith("A"));
        }

        @Test
        void opportunitiesSpanEveryCompensationTypeAndBothBeginnerModes() {
            List<Opportunity> opportunities = opportunityRepository.findAll();

            assertThat(opportunities).extracting(Opportunity::getCompensationType)
                    .contains("CREDIT", "PAID", "VOLUNTEER");
            assertThat(opportunityRepository.findByBeginnerFriendlyTrue()).isNotEmpty();
            assertThat(opportunities.stream().filter(o -> !o.isBeginnerFriendly()).toList()).isNotEmpty();
            assertThat(opportunities).allSatisfy(opportunity -> {
                assertThat(opportunity.getFacultyId()).isNotNull();
                assertThat(opportunity.getTitle()).isNotBlank();
                assertThat(opportunity.getHoursPerWeek()).isPositive();
                assertThat(opportunity.getMaxApplicants()).isPositive();
                assertThat(opportunity.getMinAcademicYear()).isBetween(1, 4);
            });
        }

        @Test
        void everyOpportunityBelongsToASeededFaculty() {
            List<Long> facultyIds = facultyRepository.findAll().stream().map(FacultyProfile::getId).toList();

            assertThat(opportunityRepository.findAll())
                    .allSatisfy(opportunity -> assertThat(facultyIds).contains(opportunity.getFacultyId()));
        }

        @Test
        void seededStudentsRespectThePrivacyDefault() {
            // Privacy rule (AGENTS.md §4): sample data must never opt a student in.
            assertThat(studentRepository.findAll())
                    .isNotEmpty()
                    .allSatisfy(student -> assertThat(student.isSearchableByFaculty()).isFalse());
            assertThat(studentRepository.findBySearchableByFacultyTrue()).isEmpty();
        }

        @Test
        void seededStudentsHaveUsableProfiles() {
            List<StudentProfile> students = studentRepository.findAll();

            assertThat(students).allSatisfy(student -> {
                assertThat(student.getName()).isNotBlank();
                assertThat(student.getEmail()).contains("@");
                assertThat(student.getAcademicYear()).isBetween(1, 4);
            });
        }

        @Test
        void atLeastOneSeededStudentHasVerifiedSkillEvidence() {
            // A seeded fixture with verified skills lets the demo/tests exercise the evidence scoring layer.
            assertThat(studentRepository.findAll())
                    .anySatisfy(student -> assertThat(student.getVerifiedSkills()).isNotEmpty());
        }

        @Test
        void seedingIsIdempotent() {
            long opportunitiesBefore = opportunityRepository.count();
            long studentsBefore = studentRepository.count();
            long facultiesBefore = facultyRepository.count();

            dataSeeder.run();

            assertThat(opportunityRepository.count()).isEqualTo(opportunitiesBefore);
            assertThat(studentRepository.count()).isEqualTo(studentsBefore);
            assertThat(facultyRepository.count()).isEqualTo(facultiesBefore);
        }
    }
}
