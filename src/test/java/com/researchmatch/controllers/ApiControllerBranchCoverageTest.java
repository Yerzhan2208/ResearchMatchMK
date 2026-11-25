package com.researchmatch.controllers;

import com.researchmatch.models.FacultyProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Targeted coverage for {@link ApiController}'s null-request guard. A literal JSON {@code null} body
 * deserialises to {@code null}; with a faculty present, the guard at line 139 must reject it (4xx), not NPE.
 * Kept separate from Member 4's {@code ApiControllerTest}.
 */
class ApiControllerBranchCoverageTest {

    private MockMvc mvc;
    private DemoRoleSession session;

    @BeforeEach
    void setUp() {
        ResearchMatchFacade facade = mock(ResearchMatchFacade.class);
        session = mock(DemoRoleSession.class);
        FacultyProfile faculty = new FacultyProfile();
        faculty.setId(3L);
        when(session.currentFaculty()).thenReturn(faculty);
        mvc = MockMvcBuilders.standaloneSetup(new ApiController(facade, session)).build();
    }

    @Test
    void createOpportunity_nullBodyRejected() throws Exception {
        mvc.perform(post("/api/opportunities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void createOpportunity_nullRequestDirectCallHitsGuard() {
        // Spring rejects an empty/null JSON body before the method body; call directly to cover the guard.
        ApiController controller = new ApiController(mock(ResearchMatchFacade.class), session);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.createOpportunity(null))
                .isInstanceOf(com.researchmatch.config.InvalidRequestException.class)
                .hasMessageContaining("Request body is required");
    }
}
