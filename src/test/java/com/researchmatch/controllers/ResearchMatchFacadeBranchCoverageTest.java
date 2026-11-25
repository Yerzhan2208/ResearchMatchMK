package com.researchmatch.controllers;

import com.researchmatch.adapters.ImportedEvidence;
import com.researchmatch.adapters.ResearchDataAdapter;
import com.researchmatch.config.ResearchDataSourceRegistry;
import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.matching.MatchingEngine;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.pipeline.RecruitmentPipeline;
import com.researchmatch.repository.ApplicationRepository;
import com.researchmatch.repository.FacultyRepository;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.SkillEvidenceRepository;
import com.researchmatch.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Targeted coverage for the defensive branches in {@link ResearchMatchFacade#importSkills} (null verified
 * skills, null imported elements) and {@link ResearchMatchFacade#getOpportunity} (null id). Kept separate
 * from Member 4's {@code ResearchMatchFacadeTest} so that file is not modified.
 */
class ResearchMatchFacadeBranchCoverageTest {

    private StudentRepository studentRepository;
    private FacultyRepository facultyRepository;
    private OpportunityRepository opportunityRepository;
    private ApplicationRepository applicationRepository;
    private SkillEvidenceRepository skillEvidenceRepository;
    private MatchingEngine matchingEngine;
    private RecruitmentPipeline recruitmentPipeline;
    private ResearchDataSourceRegistry registry;
    private ResearchMatchFacade facade;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        facultyRepository = mock(FacultyRepository.class);
        opportunityRepository = mock(OpportunityRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        skillEvidenceRepository = mock(SkillEvidenceRepository.class);
        matchingEngine = mock(MatchingEngine.class);
        recruitmentPipeline = mock(RecruitmentPipeline.class);
        registry = mock(ResearchDataSourceRegistry.class);
        facade = new ResearchMatchFacade(studentRepository, facultyRepository, opportunityRepository,
                applicationRepository, skillEvidenceRepository, matchingEngine, recruitmentPipeline, registry);
    }

    @Test
    void getOpportunity_nullIdThrows() {
        assertThatThrownBy(() -> facade.getOpportunity(null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Opportunity not found");
    }

    @Test
    void getOpportunity_unknownIdThrowsNotFound() {
        when(opportunityRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.getOpportunity(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void importSkills_nullVerifiedSkillsAndNullImportedElement() {
        StudentProfile student = new StudentProfile();
        student.setId(7L);
        student.setEmail("s@x.edu");
        student.setVerifiedSkills(null);
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        ResearchDataAdapter adapter = mock(ResearchDataAdapter.class);
        List<ImportedEvidence> imported = new ArrayList<>();
        imported.add(null); // null element must be skipped (toEvidence null guard)
        imported.add(new ImportedEvidence("Java", "repo", "snippet", 0.9));
        when(adapter.importEvidence(anyString())).thenReturn(imported);
        when(registry.forSource("github")).thenReturn(Optional.of(adapter));
        when(skillEvidenceRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        facade.importSkills(7L, "github", "octocat");

        ArgumentCaptor<StudentProfile> captor = ArgumentCaptor.forClass(StudentProfile.class);
        org.mockito.Mockito.verify(studentRepository).save(captor.capture());
        // null verifiedSkills branch produced a fresh set containing the imported skill.
        assertThat(captor.getValue().getVerifiedSkills()).contains("Java");
    }

    @Test
    void toEvidence_nullImportedListYieldsEmpty() {
        assertThat(facade.toEvidence(1L, null)).isEmpty();
        assertThat(facade.toEvidence(1L, List.of())).isEmpty();
    }
}
