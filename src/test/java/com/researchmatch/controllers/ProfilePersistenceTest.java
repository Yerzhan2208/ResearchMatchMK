package com.researchmatch.controllers;

import com.researchmatch.models.SkillEvidence;
import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.SkillEvidenceRepository;
import com.researchmatch.repository.StudentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfilePersistenceTest {

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository students;
    @Autowired private SkillEvidenceRepository evidence;
    @Autowired private EntityManager entityManager;
    @Autowired private ResearchMatchFacade facade;

    @Test
    void savesPartialProfileWithoutOverwritingVerifiedSkillsOrImplicitlyChangingConsent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/api/session").session(session)).andExpect(status().isOk());
        StudentProfile student = demoStudent();
        assertThat(student.isSearchableByFaculty()).isFalse();
        student.getVerifiedSkills().add("Java");
        flushAndClear();

        mvc.perform(post("/profile").session(session).param("searchableByFaculty", "true"))
                .andExpect(flash().attributeExists("successMessage"));
        flushAndClear();
        mvc.perform(post("/profile").session(session).param("major", "Data Science"))
                .andExpect(flash().attributeExists("successMessage"));
        flushAndClear();
        StudentProfile edited = demoStudent();
        assertThat(edited.getMajor()).isEqualTo("Data Science");
        assertThat(edited.getName()).isEqualTo(student.getName());
        assertThat(edited.getVerifiedSkills()).contains("Java");
        assertThat(edited.getHoursPerWeek()).isEqualTo(student.getHoursPerWeek());
        assertThat(edited.isSearchableByFaculty()).isTrue();

        mvc.perform(post("/profile").session(session).param("searchableByFaculty", "").param("hoursPerWeek", ""))
                .andExpect(flash().attributeExists("successMessage"));
        flushAndClear();
        assertThat(demoStudent().isSearchableByFaculty()).isTrue();
        assertThat(demoStudent().getHoursPerWeek()).isNull();

        mvc.perform(post("/profile").session(session).param("searchableByFaculty", "false"))
                .andExpect(flash().attributeExists("successMessage"));
        flushAndClear();
        assertThat(demoStudent().isSearchableByFaculty()).isFalse();
    }

    @Test
    void importsTheSameOfflineEvidenceWithoutCreatingNewRows() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/profile/import").session(session).param("source", "github"))
                .andExpect(flash().attributeExists("successMessage"));
        flushAndClear();
        Long studentId = demoStudent().getId();
        var firstImportIds = evidence.findByStudentId(studentId).stream().map(SkillEvidence::getId).toList();
        assertThat(firstImportIds).isNotEmpty();

        mvc.perform(post("/profile/import").session(session).param("source", "github"))
                .andExpect(flash().attributeExists("successMessage"));
        flushAndClear();
        var reimported = evidence.findByStudentId(studentId);
        assertThat(reimported).extracting(SkillEvidence::getId).containsExactlyInAnyOrderElementsOf(firstImportIds);
        assertThat(reimported).noneMatch(SkillEvidence::isVerifiedByStudent);
    }

    @Test
    void professorDashboardFollowsTheSelectedDemoRole() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/professor").session(session)).andExpect(redirectedUrl("/discover"));
        mvc.perform(post("/switch-role").session(session).param("role", "professor"))
                .andExpect(redirectedUrl("/professor"));
        mvc.perform(get("/professor").session(session)).andExpect(status().isOk());
    }

    @Test
    void profilePatchRejectsMissingStudent() {
        assertThatThrownBy(() -> facade.updateStudentProfile(-1L, s -> s.setMajor("CS")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private StudentProfile demoStudent() {
        return students.findByEmail(DemoRoleSession.DEMO_STUDENT_EMAIL).orElseThrow();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
