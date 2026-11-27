package com.researchmatch.controllers;

import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.FacultyRepository;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Session-scoped "Demo Role Switcher" state for the web UI.
 *
 * <p>There is deliberately no authentication (see the project brief): a single demo visitor can flip between
 * the <em>student</em> and <em>professor</em> views. This component remembers the chosen role and the resolved
 * demo student/faculty ids for the browser session. It holds no business rules; it only resolves and caches
 * identities so controllers stay thin. The selected view gates the professor dashboard and candidate API;
 * it is demo navigation state, not authentication.
 */
@Component
@SessionScope
public class DemoRoleSession {

    /** The two demo roles. */
    public enum Role {
        STUDENT,
        PROFESSOR
    }

    static final String DEMO_STUDENT_EMAIL = "demo.student@researchmatch.local";
    static final String DEMO_STUDENT_NAME = "Demo Student";

    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;
    private final OpportunityRepository opportunityRepository;

    private Role role = Role.STUDENT;
    private Long studentId;
    private Long facultyId;

    public DemoRoleSession(StudentRepository studentRepository,
                           FacultyRepository facultyRepository,
                           OpportunityRepository opportunityRepository) {
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.opportunityRepository = opportunityRepository;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        if (role != null) {
            this.role = role;
        }
    }

    /** True when the current demo view is the student's. */
    public boolean isStudentView() {
        return role == Role.STUDENT;
    }

    /**
     * Resolves (and lazily creates) the demo student for this session.
     *
     * <p>Never touches privacy: a freshly created demo student keeps {@code searchableByFaculty == false}
     * from the entity default. Returns {@code null} only if persistence is unavailable.
     */
    public StudentProfile currentStudent() {
        if (studentId == null) {
            StudentProfile student = studentRepository.findByEmail(DEMO_STUDENT_EMAIL)
                    .orElseGet(this::createDemoStudent);
            if (student != null) {
                studentId = student.getId();
            }
        }
        if (studentId == null) {
            return null;
        }
        return studentRepository.findById(studentId).orElse(null);
    }

    private StudentProfile createDemoStudent() {
        StudentProfile student = new StudentProfile();
        student.setName(DEMO_STUDENT_NAME);
        student.setEmail(DEMO_STUDENT_EMAIL);
        student.setMajor("Computer Science");
        student.setAcademicYear(3);
        student.setHoursPerWeek(10);
        student.setGithubUsername("demo-mei-ling-ho");
        // Realistic interests + claimed skills so the discovery feed produces non-zero, meaningful scores
        // (issue #20). These are the student's own claims, not verified evidence, so evidence scoring stays
        // honest — verified skills only come from a real external lookup.
        student.setInterests(new LinkedHashSet<>(List.of("Machine Learning", "Natural Language Processing",
                "Data Mining")));
        student.setClaimedSkills(new LinkedHashSet<>(List.of("Python", "SQL")));
        // Privacy rule: the entity default (false) is intentionally left untouched.
        return studentRepository.save(student);
    }

    /**
     * Resolves the demo faculty: the lowest-id faculty in the database (DataSeeder guarantees one exists; if
     * not, this falls back to the lowest-id opportunity's faculty, then to creating a placeholder).
     */
    public FacultyProfile currentFaculty() {
        if (facultyId == null) {
            facultyId = resolveFacultyId();
        }
        if (facultyId == null) {
            return null;
        }
        return facultyRepository.findById(facultyId).orElse(null);
    }

    private Long resolveFacultyId() {
        List<FacultyProfile> faculties = facultyRepository.findAll();
        if (faculties != null && !faculties.isEmpty()) {
            return faculties.get(0).getId();
        }
        List<Opportunity> opportunities = opportunityRepository.findAll();
        if (opportunities != null && !opportunities.isEmpty()) {
            return opportunities.get(0).getFacultyId();
        }
        return null;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getFacultyId() {
        return facultyId;
    }
}
