package com.researchmatch.controllers;

import com.researchmatch.models.Application;
import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.SkillEvidence;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.ApplicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WebController.class)
@AutoConfigureMockMvc
class WebControllerSliceTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ResearchMatchFacade facade;

    @MockBean
    private DemoRoleSession session;

    @MockBean
    private ApplicationRepository applicationRepository;

    private StudentProfile student() {
        StudentProfile s = new StudentProfile(7L, "Ada", "ada@example.com", "CS", 3, 10, "octocat");
        return s;
    }

    private FacultyProfile faculty() {
        FacultyProfile f = new FacultyProfile();
        f.setId(3L);
        f.setName("Dr. Lee");
        f.setDepartment("CS");
        return f;
    }

    private Opportunity opportunity() {
        return new Opportunity(10L, 3L, "NLP", "desc");
    }

    private MatchResult match() {
        MatchResult m = new MatchResult(1L, 7L, 10L);
        m.setTotalScore(0.8);
        m.setEligible(true);
        m.setExplanationSummary("Great fit");
        m.setMatchedSkills(Set.of("Java"));
        m.setMissingSkills(Set.of("Python"));
        m.setRecommendedPrep("Read X");
        return m;
    }

    private Application app() {
        Application a = new Application(1L, 7L, 10L);
        a.setStatus(Application.Status.NEW);
        return a;
    }

    @Test
    void homeRedirectsToDiscoverForStudentView() throws Exception {
        when(session.isStudentView()).thenReturn(true);

        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/discover"));
    }

    @Test
    void homeRedirectsToProfessorForFacultyView() throws Exception {
        when(session.isStudentView()).thenReturn(false);

        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professor"));
    }

    @Test
    void discoverRendersRealTemplateWithFeed() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.STUDENT);
        when(session.isStudentView()).thenReturn(true);
        when(facade.recommend(eq(7L), any())).thenReturn(List.of(match()));
        when(facade.getOpportunity(10L)).thenReturn(opportunity());
        when(facade.applicationsForStudent(7L)).thenReturn(List.of());

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attribute("strategy", "balanced"))
                .andExpect(model().attribute("strategies", WebController.STRATEGIES))
                .andExpect(content().string(containsString("Research Discovery Feed")))
                .andExpect(content().string(containsString("NLP")))
                .andExpect(content().string(containsString("Great fit")))
                .andExpect(content().string(containsString("Fit score")));
    }

    @Test
    void discoverRendersEmptyStateWhenNoMatches() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.STUDENT);
        when(session.isStudentView()).thenReturn(true);
        when(facade.recommend(eq(7L), any())).thenReturn(List.of());
        when(facade.applicationsForStudent(7L)).thenReturn(List.of());

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No matches yet.")));
    }

    @Test
    void profileRendersRealTemplateWithEvidenceAndSources() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.STUDENT);
        when(session.isStudentView()).thenReturn(true);
        when(facade.evidenceFor(7L))
                .thenReturn(List.of(new SkillEvidence(1L, 7L, "Java", "repo-x", "snippet-y")));
        when(facade.importSources()).thenReturn(List.of("github", "openalex"));

        mvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attributeExists("evidence", "importSources"))
                .andExpect(content().string(containsString("My Profile")))
                .andExpect(content().string(containsString("Import evidence")))
                .andExpect(content().string(containsString("Java")))
                .andExpect(content().string(containsString("github")))
                .andExpect(content().string(containsString("Pull verified skills from GitHub or OpenAlex.")));
    }

    @Test
    void professorRendersRealTemplateWithBoardAndCandidates() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.currentStudent()).thenReturn(student());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.PROFESSOR);
        when(session.isStudentView()).thenReturn(false);
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of(opportunity()));
        when(facade.discoverCandidates(3L)).thenReturn(List.of(student()));
        when(applicationRepository.findByOpportunityId(10L)).thenReturn(List.of(app()));

        mvc.perform(get("/professor"))
                .andExpect(status().isOk())
                .andExpect(view().name("professor"))
                .andExpect(model().attributeExists("opportunities", "candidates", "board", "compensationTypes"))
                .andExpect(content().string(containsString("Professor Dashboard")))
                .andExpect(content().string(containsString("Recruitment pipeline")))
                .andExpect(content().string(containsString("Opt-in candidates")))
                .andExpect(content().string(containsString("Ada")))
                .andExpect(content().string(containsString("NEW")))
                .andExpect(content().string(containsString("Shortlist")));
    }

    @Test
    void errorTemplateRendersThroughAdviceOnUnexpectedFailure() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        when(facade.recommend(anyLong(), any())).thenReturn(List.of());
        when(facade.applicationsForStudent(7L)).thenThrow(new RuntimeException("boom"));

        mvc.perform(get("/discover"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 500))
                .andExpect(model().attribute("message", "An unexpected error occurred."))
                .andExpect(content().string(containsString("Something went wrong")))
                .andExpect(content().string(containsString("Status 500")));
    }
    // ---------------------------------------------------------------- stage 4: UI polish

    private StudentProfile bob() {
        StudentProfile b = new StudentProfile(8L, "Bob", "bob@example.com", "CS", 2, 8, "bobcat");
        b.setVerifiedSkills(Set.of("Python"));
        return b;
    }

    @Test
    void discoverShowsApplicationTitleAndAppliedBadge() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.STUDENT);
        when(session.isStudentView()).thenReturn(true);
        when(facade.recommend(eq(7L), any())).thenReturn(List.of(match()));
        when(facade.getOpportunity(10L)).thenReturn(opportunity());
        when(facade.applicationsForStudent(7L)).thenReturn(List.of(app()));

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("appliedOpportunityIds", org.hamcrest.Matchers.contains(10L)))
                .andExpect(content().string(containsString("My applications")))
                .andExpect(content().string(containsString("Applied")));
    }

    @Test
    void discoverApplicationRowFallsBackToIdWhenTitleMissing() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.STUDENT);
        when(session.isStudentView()).thenReturn(true);
        when(facade.recommend(eq(7L), any())).thenReturn(List.of());
        when(facade.getOpportunity(10L)).thenThrow(new com.researchmatch.config.ResourceNotFoundException("gone"));
        when(facade.applicationsForStudent(7L)).thenReturn(List.of(app()));

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#1")));
    }

    @Test
    void profileShowsVerifiedAndClaimedSkills() throws Exception {
        StudentProfile s = student();
        s.setVerifiedSkills(Set.of("Java"));
        s.setClaimedSkills(Set.of("Python"));
        when(session.currentStudent()).thenReturn(s);
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.STUDENT);
        when(session.isStudentView()).thenReturn(true);
        when(facade.evidenceFor(7L)).thenReturn(List.of());
        when(facade.importSources()).thenReturn(List.of("github"));

        mvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Verified skills")))
                .andExpect(content().string(containsString("Claimed skills")))
                .andExpect(content().string(containsString("Java")))
                .andExpect(content().string(containsString("Python")));
    }

    @Test
    void profileShowsPrivateBannerByDefault() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.STUDENT);
        when(session.isStudentView()).thenReturn(true);
        when(facade.evidenceFor(7L)).thenReturn(List.of());
        when(facade.importSources()).thenReturn(List.of());

        mvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your profile is private.")));
    }

    @Test
    void profileShowsDiscoverableBannerWhenOptedIn() throws Exception {
        StudentProfile s = student();
        s.setSearchableByFaculty(true);
        when(session.currentStudent()).thenReturn(s);
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.STUDENT);
        when(session.isStudentView()).thenReturn(true);
        when(facade.evidenceFor(7L)).thenReturn(List.of());
        when(facade.importSources()).thenReturn(List.of());

        mvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your profile is discoverable by faculty.")));
    }

    @Test
    void professorFilterNarrowsCandidates() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.currentStudent()).thenReturn(student());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.PROFESSOR);
        when(session.isStudentView()).thenReturn(false);
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of());
        StudentProfile ada = student();
        ada.setVerifiedSkills(Set.of("Java"));
        when(facade.discoverCandidates(3L)).thenReturn(List.of(ada, bob()));

        mvc.perform(get("/professor").param("q", "java"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("candidateQuery", "java"))
                .andExpect(content().string(containsString("Ada")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Bob"))));
    }

    @Test
    void professorEmptyQueryReturnsAll() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.currentStudent()).thenReturn(student());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.PROFESSOR);
        when(session.isStudentView()).thenReturn(false);
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of());
        when(facade.discoverCandidates(3L)).thenReturn(List.of(student(), bob()));

        mvc.perform(get("/professor"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("candidates", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void professorFilterNoMatchShowsEmptyState() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        when(session.currentStudent()).thenReturn(student());
        when(session.getRole()).thenReturn(DemoRoleSession.Role.PROFESSOR);
        when(session.isStudentView()).thenReturn(false);
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of());
        when(facade.discoverCandidates(3L)).thenReturn(List.of(student(), bob()));

        mvc.perform(get("/professor").param("q", "zzz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No candidates match your filter.")));
    }
}