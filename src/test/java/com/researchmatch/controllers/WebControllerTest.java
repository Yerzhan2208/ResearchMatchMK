package com.researchmatch.controllers;

import com.researchmatch.config.DuplicateEntityException;
import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.models.Application;
import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.SkillEvidence;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.ApplicationRepository;
import com.researchmatch.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class WebControllerTest {

    private ResearchMatchFacade facade;
    private DemoRoleSession session;
    private ApplicationRepository applicationRepository;
    private StudentRepository studentRepository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        facade = mock(ResearchMatchFacade.class);
        session = mock(DemoRoleSession.class);
        applicationRepository = mock(ApplicationRepository.class);
        studentRepository = mock(StudentRepository.class);
        WebController controller = new WebController(facade, session, applicationRepository, studentRepository);
        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        mvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(resolver).build();
    }

    private StudentProfile student() {
        StudentProfile s = new StudentProfile();
        s.setId(7L);
        s.setEmail("ada@example.com");
        s.setName("Ada");
        return s;
    }

    private StudentProfile studentWithHandle(String handle) {
        StudentProfile s = student();
        s.setGithubUsername(handle);
        return s;
    }

    private FacultyProfile faculty() {
        FacultyProfile f = new FacultyProfile();
        f.setId(3L);
        f.setName("Dr. Lee");
        return f;
    }

    // ---------------------------------------------------------------- landing + role switch

    @Test
    void homeRedirectsByRole() throws Exception {
        when(session.isStudentView()).thenReturn(true);
        mvc.perform(get("/")).andExpect(redirectedUrl("/discover"));

        when(session.isStudentView()).thenReturn(false);
        mvc.perform(get("/")).andExpect(redirectedUrl("/professor"));
    }

    @Test
    void switchRoleStudentAndProfessor() throws Exception {
        mvc.perform(post("/switch-role").param("role", "student"))
                .andExpect(redirectedUrl("/discover"))
                .andExpect(flash().attributeExists("infoMessage"));
        verify(session).setRole(DemoRoleSession.Role.STUDENT);

        mvc.perform(post("/switch-role").param("role", "PROFESSOR"))
                .andExpect(redirectedUrl("/professor"));
        verify(session).setRole(DemoRoleSession.Role.PROFESSOR);
    }

    @Test
    void switchRoleRejectsUnknownRole() throws Exception {
        mvc.perform(post("/switch-role").param("role", "wizard"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("errorMessage"));
        verify(session, never()).setRole(any());
    }

    // ---------------------------------------------------------------- discover

    @Test
    void discoverRendersFeedWithRankedResults() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());
        MatchResult match = new MatchResult(1L, 7L, 10L);
        match.setTotalScore(0.8);
        match.setEligible(true);
        when(facade.recommend(7L, "balanced")).thenReturn(List.of(match));
        Opportunity op = new Opportunity(10L, 3L, "NLP", "desc");
        when(facade.getOpportunity(10L)).thenReturn(op);
        when(facade.applicationsForStudent(7L)).thenReturn(List.of());

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attribute("strategy", "balanced"))
                .andExpect(model().attribute("strategies", WebController.STRATEGIES))
                .andExpect(model().attributeExists("feed", "applications"));
    }

    @Test
    void discoverFallsBackToBalancedForUnknownStrategy() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(facade.recommend(7L, "balanced")).thenReturn(List.of());

        mvc.perform(get("/discover").param("strategy", "nonsense"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("strategy", "balanced"));
    }

    @Test
    void discoverUsesRequestedStrategyAndSkipsMissingOpportunity() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        MatchResult match = new MatchResult(1L, 7L, 99L);
        when(facade.recommend(7L, "skill-strict")).thenReturn(List.of(match));
        when(facade.getOpportunity(99L)).thenThrow(new ResourceNotFoundException("gone"));

        mvc.perform(get("/discover").param("strategy", "skill-strict"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("strategy", "skill-strict"))
                .andExpect(model().attribute("feed", List.of()));
    }

    @Test
    void discoverShowsErrorWhenMatchingEngineUnavailable() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(facade.recommend(anyLong(), any())).thenThrow(new UnsupportedOperationException("TODO (Member 2)"));

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("feedError", "TODO (Member 2)"));
    }

    @Test
    void discoverWithoutStudentShowsError() throws Exception {
        when(session.currentStudent()).thenReturn(null);
        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("feedError", "No demo student is available."));
    }

    @Test
    void discoverSkipsMatchWithNullOpportunityId() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        MatchResult match = new MatchResult(1L, 7L, null);
        when(facade.recommend(7L, "balanced")).thenReturn(List.of(match));

        mvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("feed", List.of()));
    }

    // ---------------------------------------------------------------- apply

    @Test
    void applySubmitsSuccessfully() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        mvc.perform(post("/apply").param("opportunityId", "10").param("statement", "I am keen"))
                .andExpect(redirectedUrl("/discover"))
                .andExpect(flash().attributeExists("successMessage"));
        verify(facade).submitApplication(7L, 10L, "I am keen");
    }

    @Test
    void applyValidatesInputs() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        mvc.perform(post("/apply").param("statement", "x"))
                .andExpect(flash().attributeExists("errorMessage"));

        mvc.perform(post("/apply").param("opportunityId", "10"))
                .andExpect(flash().attributeExists("errorMessage"));

        mvc.perform(post("/apply").param("opportunityId", "10").param("statement", "   "))
                .andExpect(flash().attributeExists("errorMessage"));

        mvc.perform(post("/apply").param("opportunityId", "10").param("statement", "a".repeat(4001)))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(facade, never()).submitApplication(anyLong(), anyLong(), anyString());
    }

    @Test
    void applyWithoutStudentShowsError() throws Exception {
        when(session.currentStudent()).thenReturn(null);
        mvc.perform(post("/apply").param("opportunityId", "10").param("statement", "x"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void applyTranslatesFacadeException() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(facade.submitApplication(7L, 10L, "hi")).thenThrow(new InvalidRequestException("already applied"));
        mvc.perform(post("/apply").param("opportunityId", "10").param("statement", "hi"))
                .andExpect(flash().attribute("errorMessage", "already applied"));
    }

    // ---------------------------------------------------------------- profile

    @Test
    void profileRendersEvidenceAndSources() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(facade.evidenceFor(7L)).thenReturn(List.of(new SkillEvidence(1L, 7L, "Java", "r", "s")));
        when(facade.importSources()).thenReturn(List.of("github", "openalex"));

        mvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attributeExists("evidence", "importSources"));
    }

    @Test
    void profileWithoutStudentUsesEmptyEvidence() throws Exception {
        when(session.currentStudent()).thenReturn(null);
        when(facade.importSources()).thenReturn(List.of("github"));

        mvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("evidence", List.of()));
    }

    @Test
    void saveProfileValidatesAndDelegates() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        mvc.perform(post("/profile")
                        .param("name", "Ada")
                        .param("email", "ada@example.com")
                        .param("major", "CS")
                        .param("academicYear", "3")
                        .param("hoursPerWeek", "10")
                        .param("interests", "NLP, AI")
                        .param("claimedSkills", "Java")
                        .param("searchableByFaculty", "true"))
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attributeExists("successMessage"));
        verify(facade).saveStudent(any(StudentProfile.class));
    }

    @Test
    void saveProfileTranslatesException() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(facade.saveStudent(any())).thenThrow(new DuplicateEntityException("dup"));
        mvc.perform(post("/profile").param("name", "Ada").param("email", "x@y.com"))
                .andExpect(flash().attribute("errorMessage", "dup"));
    }

    @Test
    void saveProfileWithoutStudentShowsError() throws Exception {
        when(session.currentStudent()).thenReturn(null);
        mvc.perform(post("/profile").param("name", "Ada"))
                .andExpect(flash().attributeExists("errorMessage"));
        verify(facade, never()).saveStudent(any());
    }

    @Test
    void importSkillsUsesTheStudentsOwnHandleNotAPostedOne() throws Exception {
        when(session.currentStudent()).thenReturn(studentWithHandle("demo-mei-ling-ho"));

        when(facade.importSkills(7L, "github", "demo-mei-ling-ho")).thenReturn(List.of());
        mvc.perform(post("/profile/import").param("source", "github"))
                .andExpect(flash().attributeExists("infoMessage"));

        when(facade.importSkills(7L, "github", "demo-mei-ling-ho"))
                .thenReturn(List.of(new SkillEvidence(1L, 7L, "Java", "r", "s")));
        mvc.perform(post("/profile/import").param("source", "github"))
                .andExpect(flash().attributeExists("successMessage"));

        when(facade.importSkills(7L, "bad", "demo-mei-ling-ho"))
                .thenThrow(new InvalidRequestException("Unknown import source"));
        mvc.perform(post("/profile/import").param("source", "bad"))
                .andExpect(flash().attribute("errorMessage", "Unknown import source"));
    }

    @Test
    void importSkillsIgnoresAHandleSubmittedInTheForm() throws Exception {
        // The handle field is gone from the UI; a posted one must not be able to override the student's own.
        when(session.currentStudent()).thenReturn(studentWithHandle("demo-mei-ling-ho"));
        when(facade.importSkills(7L, "github", "demo-mei-ling-ho")).thenReturn(List.of());

        mvc.perform(post("/profile/import").param("source", "github").param("handle", "someone-else"))
                .andExpect(flash().attributeExists("infoMessage"));

        verify(facade).importSkills(7L, "github", "demo-mei-ling-ho");
        verify(facade, never()).importSkills(7L, "github", "someone-else");
    }

    @Test
    void importSkillsWithoutAStoredHandleShowsInfo() throws Exception {
        when(session.currentStudent()).thenReturn(student());

        mvc.perform(post("/profile/import").param("source", "github"))
                .andExpect(flash().attributeExists("infoMessage"));

        verify(facade, never()).importSkills(any(), any(), any());
    }

    @Test
    void importSkillsWithoutStudentShowsError() throws Exception {
        when(session.currentStudent()).thenReturn(null);
        mvc.perform(post("/profile/import").param("source", "github"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // ---------------------------------------------------------------- professor

    @Test
    void professorRendersBoardAndCandidates() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        Opportunity op = new Opportunity(10L, 3L, "NLP", "d");
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of(op));
        when(facade.discoverCandidates(3L)).thenReturn(List.of(student()));
        Application app = new Application(1L, 7L, 10L);
        app.setStatus(Application.Status.NEW);
        when(applicationRepository.findByOpportunityId(10L)).thenReturn(List.of(app));

        mvc.perform(get("/professor"))
                .andExpect(status().isOk())
                .andExpect(view().name("professor"))
                .andExpect(model().attributeExists("opportunities", "candidates", "board", "compensationTypes"));
    }

    @Test
    void professorWithoutFacultyHasEmptyData() throws Exception {
        when(session.currentFaculty()).thenReturn(null);
        mvc.perform(get("/professor"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("opportunities", List.of()))
                .andExpect(model().attribute("candidates", List.of()));
    }

    @Test
    void professorSurfacesCandidateError() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of());
        when(facade.discoverCandidates(3L)).thenThrow(new ResourceNotFoundException("nope"));

        mvc.perform(get("/professor"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("candidateError", "nope"));
    }

    @Test
    void professorBoardSkipsNullsAndGroups() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        Opportunity op = new Opportunity(10L, 3L, "NLP", "d");
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of(op));
        when(facade.discoverCandidates(3L)).thenReturn(List.of());
        Application shortlisted = new Application(1L, 7L, 10L);
        shortlisted.setStatus(Application.Status.SHORTLISTED);
        when(applicationRepository.findByOpportunityId(10L))
                .thenReturn(java.util.Arrays.asList(null, shortlisted));

        mvc.perform(get("/professor"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("board"));
    }

    @Test
    void professorBoardHandlesNullApplications() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        Opportunity op = new Opportunity(10L, 3L, "NLP", "d");
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of(op));
        when(facade.discoverCandidates(3L)).thenReturn(List.of());
        when(applicationRepository.findByOpportunityId(10L)).thenReturn(null);

        mvc.perform(get("/professor")).andExpect(status().isOk());
    }

    @Test
    void professorBoardCarriesStudentNameAndStatement() throws Exception {
        // The board used to print "Student <id>" and dropped the statement entirely, so a professor could
        // review an application knowing neither who applied nor what they wrote.
        when(session.currentFaculty()).thenReturn(faculty());
        Opportunity op = new Opportunity(10L, 3L, "NLP Research Assistant", "d");
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of(op));
        when(facade.discoverCandidates(3L)).thenReturn(List.of());

        Application app = new Application(1L, 7L, 10L);
        app.setStatus(Application.Status.NEW);
        app.setStatement("I built a tokeniser and would love to join.");
        when(applicationRepository.findByOpportunityId(10L)).thenReturn(List.of(app));
        when(studentRepository.findById(7L)).thenReturn(java.util.Optional.of(student()));

        MvcResult result = mvc.perform(get("/professor")).andExpect(status().isOk()).andReturn();

        @SuppressWarnings("unchecked")
        Map<String, List<ApplicationCard>> board =
                (Map<String, List<ApplicationCard>>) result.getModelAndView().getModel().get("board");
        assertThat(board.get("NEW")).hasSize(1);
        ApplicationCard card = board.get("NEW").get(0);
        assertThat(card.studentName()).isEqualTo("Ada");
        assertThat(card.statement()).isEqualTo("I built a tokeniser and would love to join.");
        assertThat(card.opportunityTitle()).isEqualTo("NLP Research Assistant");
    }

    @Test
    void applicationCardFallsBackWhenStudentOrDataIsMissing() {
        // A deleted student row must not blank the card out: the template still needs something to show.
        Application app = new Application(1L, null, 10L);
        app.setStatement(null);

        ApplicationCard noStudent = new ApplicationCard(app, null, null);
        assertThat(noStudent.studentName()).isEqualTo("Student null");
        assertThat(noStudent.statement()).isEmpty();
        assertThat(noStudent.opportunityTitle()).isEqualTo("Opportunity 10");

        StudentProfile blank = new StudentProfile();
        blank.setName("   ");
        Opportunity untitled = new Opportunity(10L, 3L, "  ", "d");
        ApplicationCard blankish = new ApplicationCard(app, blank, untitled);
        assertThat(blankish.studentName()).isEqualTo("Student null");
        assertThat(blankish.opportunityTitle()).isEqualTo("Opportunity 10");
    }

    @Test
    void applicationCardHandlesNullStudentId() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        Opportunity op = new Opportunity(10L, 3L, "NLP", "d");
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of(op));
        when(facade.discoverCandidates(3L)).thenReturn(List.of());
        Application orphan = new Application(1L, null, 10L);
        orphan.setStatus(Application.Status.NEW);
        when(applicationRepository.findByOpportunityId(10L)).thenReturn(List.of(orphan));

        mvc.perform(get("/professor")).andExpect(status().isOk());
        verify(studentRepository, never()).findById(any());
    }

    @Test
    void createOpportunityDelegates() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        mvc.perform(post("/opportunities")
                        .param("title", "NLP")
                        .param("description", "d")
                        .param("requiredSkills", "Java, Python")
                        .param("preferredSkills", "NLP")
                        .param("hoursPerWeek", "8")
                        .param("compensationType", "CREDIT")
                        .param("beginnerFriendly", "true")
                        .param("maxApplicants", "3"))
                .andExpect(redirectedUrl("/professor"))
                .andExpect(flash().attributeExists("successMessage"));
        verify(facade).saveOpportunity(any(Opportunity.class));
    }

    @Test
    void createOpportunityTranslatesBuildError() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        // Missing title -> OpportunityBuilder throws InvalidRequestException
        mvc.perform(post("/opportunities").param("description", "d"))
                .andExpect(redirectedUrl("/professor"))
                .andExpect(flash().attributeExists("errorMessage"));
        verify(facade, never()).saveOpportunity(any());
    }

    @Test
    void createOpportunityWithoutFacultyShowsError() throws Exception {
        when(session.currentFaculty()).thenReturn(null);
        mvc.perform(post("/opportunities").param("title", "x"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void transitionRoutesToFacade() throws Exception {
        Application app = new Application(1L, 7L, 10L);
        when(facade.shortlist(1L)).thenReturn(app);
        when(facade.scheduleInterview(1L)).thenReturn(app);
        when(facade.accept(1L)).thenReturn(app);
        when(facade.reject(1L)).thenReturn(app);

        for (String action : List.of("shortlist", "interview", "accept", "reject")) {
            mvc.perform(post("/applications/1/transition").param("action", action))
                    .andExpect(redirectedUrl("/professor"))
                    .andExpect(flash().attributeExists("successMessage"));
        }
    }

    @Test
    void transitionRejectsUnknownActionAndTranslatesErrors() throws Exception {
        mvc.perform(post("/applications/1/transition").param("action", "bogus"))
                .andExpect(flash().attributeExists("errorMessage"));

        when(facade.shortlist(2L)).thenThrow(new com.researchmatch.config.IllegalStateTransitionException("no"));
        mvc.perform(post("/applications/2/transition").param("action", "shortlist"))
                .andExpect(flash().attribute("errorMessage", "no"));
    }
}
