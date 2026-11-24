package com.researchmatch.controllers;

import com.researchmatch.adapters.GitHubSkillAdapter;
import com.researchmatch.adapters.OpenAlexPublicationAdapter;
import com.researchmatch.config.AdapterRegistry;
import com.researchmatch.matching.MatchingEngine;
import com.researchmatch.pipeline.RecruitmentPipeline;
import com.researchmatch.repository.ApplicationRepository;
import com.researchmatch.repository.FacultyRepository;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.SkillEvidenceRepository;
import com.researchmatch.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Member 4's facade is the contract Member 5 tests against end-to-end. This unit test pins the wiring and
 * makes every unimplemented entry point fail loudly instead of pretending to succeed.
 */
class ResearchMatchFacadeTest {

    private StudentRepository studentRepository;
    private FacultyRepository facultyRepository;
    private OpportunityRepository opportunityRepository;
    private ApplicationRepository applicationRepository;
    private SkillEvidenceRepository skillEvidenceRepository;
    private MatchingEngine matchingEngine;
    private RecruitmentPipeline recruitmentPipeline;
    private AdapterRegistry adapterRegistry;
    private ResearchMatchFacade facade;

    @BeforeEach
    void setUp() {
        studentRepository = Mockito.mock(StudentRepository.class);
        facultyRepository = Mockito.mock(FacultyRepository.class);
        opportunityRepository = Mockito.mock(OpportunityRepository.class);
        applicationRepository = Mockito.mock(ApplicationRepository.class);
        skillEvidenceRepository = Mockito.mock(SkillEvidenceRepository.class);
        matchingEngine = Mockito.mock(MatchingEngine.class);
        recruitmentPipeline = Mockito.mock(RecruitmentPipeline.class);
        adapterRegistry = new AdapterRegistry(List.of(new GitHubSkillAdapter(), new OpenAlexPublicationAdapter()));

        facade = new ResearchMatchFacade(studentRepository, facultyRepository, opportunityRepository,
                applicationRepository, skillEvidenceRepository, matchingEngine, recruitmentPipeline,
                adapterRegistry);
    }

    @Test
    void exposesItsCollaborators() {
        assertThat(facade.getStudentRepository()).isSameAs(studentRepository);
        assertThat(facade.getFacultyRepository()).isSameAs(facultyRepository);
        assertThat(facade.getOpportunityRepository()).isSameAs(opportunityRepository);
        assertThat(facade.getApplicationRepository()).isSameAs(applicationRepository);
        assertThat(facade.getSkillEvidenceRepository()).isSameAs(skillEvidenceRepository);
        assertThat(facade.getMatchingEngine()).isSameAs(matchingEngine);
        assertThat(facade.getRecruitmentPipeline()).isSameAs(recruitmentPipeline);
        assertThat(facade.getAdapterRegistry()).isSameAs(adapterRegistry);
    }

    @Test
    void listsImportSourcesForTheUi() {
        assertThat(facade.importSources()).containsExactly(GitHubSkillAdapter.PLATFORM,
                OpenAlexPublicationAdapter.PLATFORM);
    }

    @Test
    void everyMember4EntryPointIsAnExplicitTodo() {
        assertThatThrownBy(() -> facade.findStudent(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("findStudent");
        assertThatThrownBy(() -> facade.getStudent(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("getStudent");
        assertThatThrownBy(() -> facade.saveStudent(new com.researchmatch.models.StudentProfile()))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("saveStudent");
        assertThatThrownBy(() -> facade.importSkills(1L, "github", "octocat"))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("importSkills");
        assertThatThrownBy(() -> facade.evidenceFor(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("evidenceFor");
        assertThatThrownBy(() -> facade.getOpportunity(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("getOpportunity");
        assertThatThrownBy(() -> facade.saveOpportunity(new com.researchmatch.models.Opportunity()))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("saveOpportunity");
        assertThatThrownBy(() -> facade.opportunitiesForFaculty(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("opportunitiesForFaculty");
        assertThatThrownBy(() -> facade.getFaculty(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("getFaculty");
        assertThatThrownBy(() -> facade.match(1L, 2L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("match");
        assertThatThrownBy(() -> facade.recommend(1L, null))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("recommend");
        assertThatThrownBy(() -> facade.submitApplication(1L, 2L, "hi"))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("submitApplication");
        assertThatThrownBy(() -> facade.applicationsForStudent(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("applicationsForStudent");
        assertThatThrownBy(() -> facade.discoverCandidates(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("discoverCandidates");
        assertThatThrownBy(() -> facade.shortlist(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("shortlist");
        assertThatThrownBy(() -> facade.scheduleInterview(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("scheduleInterview");
        assertThatThrownBy(() -> facade.accept(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("accept");
        assertThatThrownBy(() -> facade.reject(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("reject");
    }

    @Test
    void facadeNeverTouchesPersistenceFromThePlaceholders() {
        Mockito.verifyNoInteractions(studentRepository, facultyRepository, opportunityRepository,
                applicationRepository, skillEvidenceRepository, matchingEngine, recruitmentPipeline);
    }
}
