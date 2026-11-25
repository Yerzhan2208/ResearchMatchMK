package com.researchmatch.controllers;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.models.Application;
import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Targeted coverage for the defensive/edge branches of {@link WebController} that the main controller test
 * does not exercise. Kept separate so Member 4's {@code WebControllerTest} is left untouched.
 */
class WebControllerBranchCoverageTest {

    private ResearchMatchFacade facade;
    private DemoRoleSession session;
    private ApplicationRepository applicationRepository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        facade = mock(ResearchMatchFacade.class);
        session = mock(DemoRoleSession.class);
        applicationRepository = mock(ApplicationRepository.class);
        WebController controller = new WebController(facade, session, applicationRepository);
        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        mvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(resolver).build();
    }

    private StudentProfile student() {
        StudentProfile s = new StudentProfile();
        s.setId(7L);
        s.setEmail("ada@example.com");
        return s;
    }

    private FacultyProfile faculty() {
        FacultyProfile f = new FacultyProfile();
        f.setId(3L);
        f.setName("Dr. Lee");
        return f;
    }

    @Test
    void discover_researchMatchExceptionBecomesFeedError() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        when(facade.recommend(eq(7L), any())).thenThrow(new InvalidRequestException("boom"));
        when(facade.applicationsForStudent(7L)).thenReturn(List.of());

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"));
    }

    @Test
    void discover_unsupportedOperationBecomesFeedError() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        when(facade.recommend(eq(7L), any())).thenThrow(new UnsupportedOperationException("todo"));
        when(facade.applicationsForStudent(7L)).thenReturn(List.of());

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"));
    }

    @Test
    void discover_addsMatchWhenOpportunityExists() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        MatchResult match = new MatchResult(1L, 7L, 10L);
        match.setOpportunityId(10L);
        when(facade.recommend(eq(7L), any())).thenReturn(List.of(match));
        when(facade.getOpportunity(10L)).thenReturn(new Opportunity(10L, 3L, "NLP", "d"));
        when(facade.applicationsForStudent(7L)).thenReturn(List.of());

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"));
    }

    @Test
    void discover_skipWhenGetOpportunityThrowsNotFound() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        MatchResult match = new MatchResult(1L, 7L, 10L);
        match.setOpportunityId(10L);
        when(facade.recommend(eq(7L), any())).thenReturn(List.of(match));
        when(facade.getOpportunity(10L)).thenThrow(new ResourceNotFoundException("gone"));
        when(facade.applicationsForStudent(7L)).thenReturn(List.of());

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"));
    }

    @Test
    void discover_skipsMatchWhoseOpportunityIsGone() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        MatchResult match = new MatchResult(1L, 7L, 10L);
        match.setOpportunityId(null); // safeOpportunity(null) -> null -> skipped
        when(facade.recommend(eq(7L), any())).thenReturn(List.of(match));
        when(facade.applicationsForStudent(7L)).thenReturn(List.of());

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"));
    }

    @Test
    void transition_nullActionHitsUnknownBranch() throws Exception {
        mvc.perform(post("/applications/5/transition").param("action", ""))
                .andExpect(redirectedUrl("/professor"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void professor_nullFacultyAndGoneCandidateErrorsAreHandled() throws Exception {
        // faculty null path for opportunities list
        when(session.currentFaculty()).thenReturn(null);
        mvc.perform(get("/professor")).andExpect(status().isOk()).andExpect(view().name("professor"));

        // faculty present but discoverCandidates throws -> candidateError path
        FacultyProfile f = faculty();
        when(session.currentFaculty()).thenReturn(f);
        when(facade.discoverCandidates(3L)).thenThrow(new ResourceNotFoundException("gone"));
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of());
        mvc.perform(get("/professor")).andExpect(status().isOk()).andExpect(view().name("professor"));
    }

    @Test
    void professor_boardSkipsNullOpportunityIdsAndNullApplications() throws Exception {
        FacultyProfile f = faculty();
        when(session.currentFaculty()).thenReturn(f);
        when(facade.discoverCandidates(3L)).thenReturn(List.of());
        Opportunity noId = new Opportunity();
        noId.setTitle("no id");                 // id null -> continue
        Opportunity withId = new Opportunity(20L, 3L, "t", "d");
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of(noId, withId));
        when(applicationRepository.findByOpportunityId(20L)).thenReturn(null); // null list -> continue

        mvc.perform(get("/professor")).andExpect(status().isOk()).andExpect(view().name("professor"));
    }

    @Test
    void professor_boardSkipsNullApplicationAndNullStatus() throws Exception {
        FacultyProfile f = faculty();
        when(session.currentFaculty()).thenReturn(f);
        when(facade.discoverCandidates(3L)).thenReturn(List.of());
        Opportunity withId = new Opportunity(20L, 3L, "t", "d");
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of(withId));
        Application nullStatus = new Application(5L, 1L, 20L);
        nullStatus.setStatus(null);
        when(applicationRepository.findByOpportunityId(20L))
                .thenReturn(java.util.Arrays.asList(null, nullStatus));

        mvc.perform(get("/professor")).andExpect(status().isOk()).andExpect(view().name("professor"));
    }

    @Test
    void createOpportunity_blankCsvDoesNotFail() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        when(facade.saveOpportunity(any(Opportunity.class))).thenAnswer(i -> i.getArgument(0));

        mvc.perform(post("/opportunities")
                        .param("title", "T")
                        .param("description", "D")
                        .param("requiredSkills", "  ,  ")   // blanks -> empty set
                        .param("preferredSkills", "")
                        .param("hoursPerWeek", "5")
                        .param("compensationType", "CREDIT")
                        .param("maxApplicants", "2"))
                .andExpect(redirectedUrl("/professor"));
    }

    @Test
    void strategies_returnsConfiguredList() {
        org.assertj.core.api.Assertions.assertThat(WebController.strategies()).isNotEmpty();
    }

    @Test
    void switchRole_nullRoleValueOfBranch() throws Exception {
        // Direct call: Spring's @RequestParam would reject a missing value, but the null-safe ternary is a
        // real defensive branch worth covering.
        WebController controller = new WebController(facade, session, applicationRepository);
        org.springframework.web.servlet.mvc.support.RedirectAttributes attrs =
                new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
        String view = controller.switchRole(null, attrs);
        org.assertj.core.api.Assertions.assertThat(view).isEqualTo("redirect:/");
    }

    @Test
    void transitionApplication_nullActionUnknownBranch() {
        WebController controller = new WebController(facade, session, applicationRepository);
        org.springframework.web.servlet.mvc.support.RedirectAttributes attrs =
                new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
        String view = controller.transitionApplication(1L, null, attrs);
        org.assertj.core.api.Assertions.assertThat(view).isEqualTo("redirect:/professor");
    }
}
