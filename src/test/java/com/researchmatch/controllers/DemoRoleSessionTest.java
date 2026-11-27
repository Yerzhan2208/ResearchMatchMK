package com.researchmatch.controllers;

import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.FacultyRepository;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoRoleSessionTest {

    private StudentRepository studentRepository;
    private FacultyRepository facultyRepository;
    private OpportunityRepository opportunityRepository;
    private DemoRoleSession session;

    @BeforeEach
    void setUp() {
        studentRepository = Mockito.mock(StudentRepository.class);
        facultyRepository = Mockito.mock(FacultyRepository.class);
        opportunityRepository = Mockito.mock(OpportunityRepository.class);
        session = new DemoRoleSession(studentRepository, facultyRepository, opportunityRepository);
    }

    @Test
    void defaultsToStudentRoleAndIgnoresNullRole() {
        assertThat(session.getRole()).isEqualTo(DemoRoleSession.Role.STUDENT);
        assertThat(session.isStudentView()).isTrue();
        session.setRole(null);
        assertThat(session.getRole()).isEqualTo(DemoRoleSession.Role.STUDENT);
        session.setRole(DemoRoleSession.Role.PROFESSOR);
        assertThat(session.isStudentView()).isFalse();
    }

    @Test
    void currentStudentUsesExistingDemoStudent() {
        StudentProfile existing = new StudentProfile();
        existing.setId(5L);
        existing.setEmail(DemoRoleSession.DEMO_STUDENT_EMAIL);
        when(studentRepository.findByEmail(DemoRoleSession.DEMO_STUDENT_EMAIL)).thenReturn(Optional.of(existing));
        when(studentRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThat(session.currentStudent()).isSameAs(existing);
        assertThat(session.getStudentId()).isEqualTo(5L);
        // second call uses cached id
        assertThat(session.currentStudent()).isSameAs(existing);
    }

    @Test
    void currentStudentCreatesDemoStudentWhenAbsent() {
        when(studentRepository.findByEmail(DemoRoleSession.DEMO_STUDENT_EMAIL)).thenReturn(Optional.empty());
        StudentProfile created = new StudentProfile();
        created.setId(9L);
        created.setEmail(DemoRoleSession.DEMO_STUDENT_EMAIL);
        when(studentRepository.save(any(StudentProfile.class))).thenReturn(created);
        when(studentRepository.findById(9L)).thenReturn(Optional.of(created));

        StudentProfile result = session.currentStudent();
        assertThat(result.getId()).isEqualTo(9L);

        // The seeded demo student must carry realistic interests/claimed skills so the feed scores non-zero.
        org.mockito.ArgumentCaptor<StudentProfile> captor =
                org.mockito.ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentRepository).save(captor.capture());
        StudentProfile seeded = captor.getValue();
        assertThat(seeded.getInterests()).isNotEmpty();
        assertThat(seeded.getClaimedSkills()).isNotEmpty();
        // Honesty: only real external lookups may mark skills verified; seeding must not fake evidence.
        assertThat(seeded.getVerifiedSkills()).isEmpty();
        assertThat(seeded.isSearchableByFaculty()).isFalse();
    }

    @Test
    void currentStudentReturnsNullWhenPersistenceUnavailable() {
        when(studentRepository.findByEmail(DemoRoleSession.DEMO_STUDENT_EMAIL)).thenReturn(Optional.empty());
        when(studentRepository.save(any(StudentProfile.class))).thenReturn(null);
        assertThat(session.currentStudent()).isNull();
    }

    @Test
    void currentFacultyPrefersLowestFaculty() {
        FacultyProfile f = new FacultyProfile();
        f.setId(3L);
        when(facultyRepository.findAll()).thenReturn(List.of(f));
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(f));

        assertThat(session.currentFaculty()).isSameAs(f);
        assertThat(session.getFacultyId()).isEqualTo(3L);
    }

    @Test
    void currentFacultyFallsBackToOpportunityFaculty() {
        when(facultyRepository.findAll()).thenReturn(List.of());
        Opportunity op = new Opportunity(1L, 4L, "t", "d");
        when(opportunityRepository.findAll()).thenReturn(List.of(op));
        FacultyProfile f = new FacultyProfile();
        f.setId(4L);
        when(facultyRepository.findById(4L)).thenReturn(Optional.of(f));

        assertThat(session.currentFaculty()).isSameAs(f);
    }

    @Test
    void currentFacultyReturnsNullWhenNothingExists() {
        when(facultyRepository.findAll()).thenReturn(List.of());
        when(opportunityRepository.findAll()).thenReturn(List.of());
        assertThat(session.currentFaculty()).isNull();
    }

    @Test
    void currentFacultyReturnsNullWhenFacultyVanishes() {
        when(facultyRepository.findAll()).thenReturn(List.of());
        Opportunity op = new Opportunity(1L, 4L, "t", "d");
        when(opportunityRepository.findAll()).thenReturn(List.of(op));
        when(facultyRepository.findById(4L)).thenReturn(Optional.empty());
        assertThat(session.currentFaculty()).isNull();
    }

    @Test
    void currentFacultyCachesResolvedId() {
        FacultyProfile f = new FacultyProfile();
        f.setId(3L);
        when(facultyRepository.findAll()).thenReturn(List.of(f));
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(f));

        assertThat(session.currentFaculty()).isSameAs(f);
        assertThat(session.currentFaculty()).isSameAs(f);

        verify(facultyRepository, times(1)).findAll();
        verify(facultyRepository, times(2)).findById(3L);
    }

    @Test
    void currentFacultyHandlesNullFacultyList() {
        when(facultyRepository.findAll()).thenReturn(null);
        Opportunity op = new Opportunity(1L, 4L, "t", "d");
        when(opportunityRepository.findAll()).thenReturn(List.of(op));
        FacultyProfile f = new FacultyProfile();
        f.setId(4L);
        when(facultyRepository.findById(4L)).thenReturn(Optional.of(f));

        assertThat(session.currentFaculty()).isSameAs(f);
    }

    @Test
    void currentFacultyHandlesNullOpportunityListWhenNoFaculty() {
        when(facultyRepository.findAll()).thenReturn(List.of());
        when(opportunityRepository.findAll()).thenReturn(null);
        assertThat(session.currentFaculty()).isNull();
    }
}
