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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiControllerTest {

    private ResearchMatchFacade facade;
    private DemoRoleSession session;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        facade = mock(ResearchMatchFacade.class);
        session = mock(DemoRoleSession.class);
        mvc = MockMvcBuilders.standaloneSetup(new ApiController(facade, session)).build();
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
        return f;
    }

    // ---------------------------------------------------------------- session

    @Test
    void sessionReturnsRoleAndIds() throws Exception {
        when(session.getRole()).thenReturn(DemoRoleSession.Role.STUDENT);
        when(session.currentStudent()).thenReturn(student());
        when(session.currentFaculty()).thenReturn(faculty());

        mvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.studentId").value(7))
                .andExpect(jsonPath("$.facultyId").value(3));
    }

    @Test
    void sessionWithoutEntitiesHasNullIds() throws Exception {
        when(session.getRole()).thenReturn(DemoRoleSession.Role.PROFESSOR);
        when(session.currentStudent()).thenReturn(null);
        when(session.currentFaculty()).thenReturn(null);

        mvc.perform(get("/api/session"))
                .andExpect(jsonPath("$.studentId").doesNotExist())
                .andExpect(jsonPath("$.facultyId").doesNotExist());
    }

    @Test
    void switchRoleUpdatesSession() throws Exception {
        mvc.perform(post("/api/session/role").param("role", "professor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROFESSOR"));
        verify(session).setRole(DemoRoleSession.Role.PROFESSOR);
    }

    @Test
    void switchRoleRejectsUnknownRole() throws Exception {
        mvc.perform(post("/api/session/role").param("role", "wizard"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---------------------------------------------------------------- discover + match

    @Test
    void discoverReturnsRankedItems() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        MatchResult match = new MatchResult(1L, 7L, 10L);
        match.setTotalScore(0.5);
        match.setEligible(true);
        when(facade.recommend(7L, null)).thenReturn(List.of(match));

        mvc.perform(get("/api/discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("balanced"))
                .andExpect(jsonPath("$.items[0].opportunityId").value(10))
                .andExpect(jsonPath("$.items[0].eligible").value(true));
    }

    @Test
    void discoverWithoutStudentIs404() throws Exception {
        when(session.currentStudent()).thenReturn(null);
        mvc.perform(get("/api/discover")).andExpect(status().isNotFound());
    }

    @Test
    void discoverWithBlankStrategyReportsBalanced() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(facade.recommend(eq(7L), any())).thenReturn(List.of());
        mvc.perform(get("/api/discover").param("strategy", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("balanced"));
    }

    @Test
    void discoverWithNamedStrategyEchoesIt() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(facade.recommend(7L, "skill-strict")).thenReturn(List.of());
        mvc.perform(get("/api/discover").param("strategy", "skill-strict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("skill-strict"));
    }

    @Test
    void matchDelegates() throws Exception {
        MatchResult match = new MatchResult(1L, 7L, 10L);
        when(facade.match(7L, 10L)).thenReturn(match);
        mvc.perform(get("/api/students/7/matches/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opportunityId").value(10));
    }

    // ---------------------------------------------------------------- applications

    @Test
    void applyCreatesApplication() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        Application app = new Application(1L, 7L, 10L);
        when(facade.submitApplication(7L, 10L, "hi")).thenReturn(app);

        mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":10,\"statement\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void applyWithoutBodyOpportunityIdIsBadRequest() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\":\"hi\"}"))
                .andExpect(status().isBadRequest());
        verify(facade, never()).submitApplication(anyLong(), anyLong(), anyString());
    }

    @Test
    void applyWithoutStudentIsNotFound() throws Exception {
        when(session.currentStudent()).thenReturn(null);
        mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":10}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void applyWithNullBodyIsBadRequest() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
        verify(facade, never()).submitApplication(anyLong(), anyLong(), anyString());
    }

    @Test
    void transitionWithNullActionNameIsBadRequest() throws Exception {
        // Omitting the required action parameter surfaces as a bad request (missing param / illegal arg).
        mvc.perform(post("/api/applications/1/transition"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applicationsListDelegates() throws Exception {
        when(facade.applicationsForStudent(7L)).thenReturn(List.of(new Application(1L, 7L, 10L)));
        mvc.perform(get("/api/students/7/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void transitionRoutesToFacade() throws Exception {
        Application app = new Application(1L, 7L, 10L);
        when(facade.shortlist(1L)).thenReturn(app);
        when(facade.scheduleInterview(1L)).thenReturn(app);
        when(facade.accept(1L)).thenReturn(app);
        when(facade.reject(1L)).thenReturn(app);

        for (String action : List.of("shortlist", "interview", "accept", "reject")) {
            mvc.perform(post("/api/applications/1/transition").param("action", action))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void transitionUnknownActionIsBadRequest() throws Exception {
        mvc.perform(post("/api/applications/1/transition").param("action", "bogus"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transitionBlankActionHitsDefaultArm() throws Exception {
        mvc.perform(post("/api/applications/1/transition").param("action", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transitionNullActionDirectlyThrowsInvalidRequest() {
        ApiController controller = new ApiController(facade, session);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.transition(1L, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void applyWithNullRequestDirectlyThrowsInvalidRequest() {
        when(session.currentStudent()).thenReturn(student());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ApiController(facade, session).apply(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createOpportunityWithNullRequestDirectlyThrowsInvalidRequest() {
        when(session.currentFaculty()).thenReturn(faculty());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ApiController(facade, session).createOpportunity(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void transitionIllegalStateIsBadRequest() throws Exception {
        when(facade.shortlist(2L)).thenThrow(new com.researchmatch.config.IllegalStateTransitionException("no"));
        mvc.perform(post("/api/applications/2/transition").param("action", "shortlist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("no"));
    }

    // ---------------------------------------------------------------- opportunities + faculty

    @Test
    void createOpportunityReturnsCreated() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        Opportunity saved = new Opportunity(10L, 3L, "NLP", "d");
        when(facade.saveOpportunity(any(Opportunity.class))).thenReturn(saved);

        mvc.perform(post("/api/opportunities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"NLP\",\"requiredSkills\":[\"Java\",\"Python\"],"
                                + "\"hoursPerWeek\":8,\"maxApplicants\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void createOpportunityWithoutFacultyIs404() throws Exception {
        when(session.currentFaculty()).thenReturn(null);
        mvc.perform(post("/api/opportunities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"NLP\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOpportunityInvalidBuildIsBadRequest() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        // Blank title -> OpportunityBuilder throws InvalidRequestException
        mvc.perform(post("/api/opportunities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"d\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOpportunityWithNullBodyIsBadRequest() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        mvc.perform(post("/api/opportunities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
        verify(facade, never()).saveOpportunity(any());
    }

    @Test
    void applyWithExplicitNullOpportunityIdIsBadRequest() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":null,\"statement\":\"x\"}"))
                .andExpect(status().isBadRequest());
        verify(facade, never()).submitApplication(anyLong(), anyLong(), anyString());
    }

    @Test
    void applyWithMalformedJsonBodyIsBadRequest() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":"))
                .andExpect(status().isBadRequest());
        verify(facade, never()).submitApplication(anyLong(), anyLong(), anyString());
    }

    @Test
    void createOpportunityWithMalformedJsonBodyIsBadRequest() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        mvc.perform(post("/api/opportunities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
        verify(facade, never()).saveOpportunity(any());
    }

    @Test
    void matchWithNonNumericPathVariableIsBadRequest() throws Exception {
        mvc.perform(get("/api/students/abc/matches/10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transitionWithNonNumericIdIsBadRequest() throws Exception {
        mvc.perform(post("/api/applications/abc/transition").param("action", "shortlist"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOpportunityWithNullSkillListsIsAccepted() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        Opportunity saved = new Opportunity(11L, 3L, "NLP", "d");
        when(facade.saveOpportunity(any(Opportunity.class))).thenReturn(saved);

        // requiredSkills / preferredSkills omitted -> null -> csv(null) yields empty lists.
        mvc.perform(post("/api/opportunities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"NLP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    void opportunitiesAndCandidatesDelegate() throws Exception {
        when(facade.opportunitiesForFaculty(3L)).thenReturn(List.of(new Opportunity(10L, 3L, "t", "d")));
        when(facade.discoverCandidates(3L)).thenReturn(List.of(student()));

        mvc.perform(get("/api/faculties/3/opportunities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
        mvc.perform(get("/api/faculties/3/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7));
    }

    // ---------------------------------------------------------------- profile + import

    @Test
    void profileReturnsCurrentStudent() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        mvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void profileWithoutStudentIs404() throws Exception {
        when(session.currentStudent()).thenReturn(null);
        mvc.perform(get("/api/profile")).andExpect(status().isNotFound());
    }

    @Test
    void importSkillsDelegates() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(facade.importSkills(7L, "github", "octocat"))
                .thenReturn(List.of(new SkillEvidence(1L, 7L, "Java", "r", "s")));
        mvc.perform(post("/api/profile/import").param("source", "github").param("handle", "octocat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skillName").value("Java"));
    }

    @Test
    void importSkillsWithoutStudentIs404() throws Exception {
        when(session.currentStudent()).thenReturn(null);
        mvc.perform(post("/api/profile/import").param("handle", "x"))
                .andExpect(status().isNotFound());
    }

    @Test
    void importSourcesReturnsList() throws Exception {
        when(facade.importSources()).thenReturn(List.of("github", "openalex"));
        mvc.perform(get("/api/import-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("github"));
    }

    @Test
    void invalidRequestMapsToBadRequest() throws Exception {
        when(session.currentStudent()).thenReturn(student());
        when(facade.recommend(7L, null)).thenThrow(new InvalidRequestException("bad"));
        mvc.perform(get("/api/discover"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad"));
    }

    @Test
    void duplicateExceptionMapsToBadRequest() throws Exception {
        when(session.currentFaculty()).thenReturn(faculty());
        when(facade.saveOpportunity(any())).thenThrow(new DuplicateEntityException("dup"));
        mvc.perform(post("/api/opportunities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"NLP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("dup"));
    }

    @Test
    void resourceNotFoundMapsTo404() throws Exception {
        when(facade.match(1L, 2L)).thenThrow(new ResourceNotFoundException("nope"));
        mvc.perform(get("/api/students/1/matches/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("nope"));
    }
}
