package com.researchmatch.controllers;

import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.models.Application;
import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebControllerHelpersTest {

    private ResearchMatchFacade facade;
    private DemoRoleSession session;
    private WebController controller;

    @BeforeEach
    void setUp() {
        facade = mock(ResearchMatchFacade.class);
        session = mock(DemoRoleSession.class);
        controller = new WebController(facade, session, mock(ApplicationRepository.class));
    }

    private StudentProfile student() {
        StudentProfile s = new StudentProfile(7L, "Ada", "ada@example.com", "CS", 3, 10, "octocat");
        return s;
    }

    private FacultyProfile faculty() {
        FacultyProfile f = new FacultyProfile();
        f.setId(3L);
        f.setName("Dr. Lee");
        return f;
    }

    @Test
    void discoverSkipsNullApplicationAndNullOpportunityId() {
        when(session.currentStudent()).thenReturn(student());
        when(facade.recommend(any(), any())).thenReturn(List.of());
        Application noOpp = new Application(2L, 7L, null);
        Application real = new Application(1L, 7L, 10L);
        real.setStatus(Application.Status.NEW);
        when(facade.applicationsForStudent(7L)).thenReturn(Arrays.asList(null, noOpp, real));
        when(facade.getOpportunity(10L)).thenReturn(new Opportunity(10L, 3L, "NLP", "d"));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.discover(null, model);

        @SuppressWarnings("unchecked")
        List<ApplicationView> views = (List<ApplicationView>) model.get("applications");
        assertThat(views).hasSize(2);
        assertThat(views.get(0).opportunityTitle()).isNull();
        assertThat(views.get(1).opportunityTitle()).isEqualTo("NLP");

        @SuppressWarnings("unchecked")
        Set<Long> ids = (Set<Long>) model.get("appliedOpportunityIds");
        assertThat(ids).containsExactly(10L);
    }

    @Test
    void discoverHandlesNullApplicationsList() {
        when(session.currentStudent()).thenReturn(student());
        when(facade.recommend(any(), any())).thenReturn(List.of());
        when(facade.applicationsForStudent(7L)).thenReturn(null);

        ExtendedModelMap model = new ExtendedModelMap();
        controller.discover(null, model);

        assertThat((List<?>) model.get("applications")).isEmpty();
        assertThat((Set<?>) model.get("appliedOpportunityIds")).isEmpty();
    }

    @Test
    void discoverFallsBackWhenOpportunityTitleBlank() {
        when(session.currentStudent()).thenReturn(student());
        when(facade.recommend(any(), any())).thenReturn(List.of());
        Application real = new Application(1L, 7L, 10L);
        when(facade.applicationsForStudent(7L)).thenReturn(List.of(real));
        when(facade.getOpportunity(10L)).thenReturn(new Opportunity(10L, 3L, "  ", "d"));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.discover(null, model);

        @SuppressWarnings("unchecked")
        List<ApplicationView> views = (List<ApplicationView>) model.get("applications");
        assertThat(views.get(0).opportunityTitle()).isNull();
    }

    @Test
    void discoverWithoutStudentSkipsApplications() {
        when(session.currentStudent()).thenReturn(null);

        ExtendedModelMap model = new ExtendedModelMap();
        controller.discover(null, model);

        assertThat((List<?>) model.get("applications")).isEmpty();
        assertThat((Set<?>) model.get("appliedOpportunityIds")).isEmpty();
    }

    @Test
    void professorFilterSkipsNullCandidateAndHandlesNullSkills() {
        when(session.currentFaculty()).thenReturn(faculty());
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of());

        StudentProfile noName = new StudentProfile();
        noName.setId(9L);
        noName.setVerifiedSkills(null);

        StudentProfile nullSkill = new StudentProfile();
        nullSkill.setId(10L);
        nullSkill.setName("Cy");
        Set<String> skills = new LinkedHashSet<>();
        skills.add(null);
        skills.add("Clojure");
        nullSkill.setVerifiedSkills(skills);

        StudentProfile ada = student();
        ada.setVerifiedSkills(Set.of("Java"));

        StudentProfile noSkills = new StudentProfile();
        noSkills.setId(11L);
        noSkills.setName("Dee");
        noSkills.setVerifiedSkills(null);

        when(facade.discoverCandidates(3L))
                .thenReturn(new ArrayList<>(Arrays.asList(null, noName, nullSkill, ada, noSkills)));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.professor("clojure", model);
        @SuppressWarnings("unchecked")
        List<StudentProfile> filtered = (List<StudentProfile>) model.get("candidates");
        assertThat(filtered).extracting(StudentProfile::getName).containsExactly("Cy");

        ExtendedModelMap model2 = new ExtendedModelMap();
        controller.professor("ada", model2);
        @SuppressWarnings("unchecked")
        List<StudentProfile> byName = (List<StudentProfile>) model2.get("candidates");
        assertThat(byName).extracting(StudentProfile::getName).containsExactly("Ada");
    }

    @Test
    void professorBlankQueryReturnsOriginalListReference() {
        when(session.currentFaculty()).thenReturn(faculty());
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of());
        StudentProfile s = student();
        when(facade.discoverCandidates(3L)).thenReturn(List.of(s));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.professor("   ", model);
        @SuppressWarnings("unchecked")
        List<StudentProfile> candidates = (List<StudentProfile>) model.get("candidates");
        assertThat(candidates).containsExactly(s);
        assertThat(model.get("candidateQuery")).isEqualTo("");
    }

    @Test
    void professorNullQueryKeepsCandidates() {
        when(session.currentFaculty()).thenReturn(faculty());
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of());
        StudentProfile s = student();
        when(facade.discoverCandidates(3L)).thenReturn(List.of(s));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.professor(null, model);
        @SuppressWarnings("unchecked")
        List<StudentProfile> candidates = (List<StudentProfile>) model.get("candidates");
        assertThat(candidates).containsExactly(s);
        assertThat(model.get("candidateQuery")).isEqualTo("");
    }

    @Test
    void professorWithoutFacultySkipsCandidates() {
        when(session.currentFaculty()).thenReturn(null);

        ExtendedModelMap model = new ExtendedModelMap();
        controller.professor("java", model);

        assertThat((String) model.get("candidateQuery")).isEqualTo("java");
        assertThat((List<?>) model.get("candidates")).isEmpty();
    }

    @Test
    void applicationViewsHandlesUnresolvableOpportunity() {
        when(session.currentStudent()).thenReturn(student());
        when(facade.recommend(any(), any())).thenReturn(List.of());
        Application real = new Application(1L, 7L, 10L);
        when(facade.applicationsForStudent(7L)).thenReturn(List.of(real));
        when(facade.getOpportunity(10L)).thenThrow(new ResourceNotFoundException("gone"));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.discover(null, model);

        @SuppressWarnings("unchecked")
        List<ApplicationView> views = (List<ApplicationView>) model.get("applications");
        assertThat(views.get(0).opportunityTitle()).isNull();
        assertThat(views.get(0).application()).isSameAs(real);
    }
}
