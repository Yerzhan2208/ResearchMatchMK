package com.researchmatch.controllers;

import com.researchmatch.adapters.GitHubSkillAdapter;
import com.researchmatch.adapters.ImportedEvidence;
import com.researchmatch.adapters.OpenAlexPublicationAdapter;
import com.researchmatch.adapters.ResearchDataAdapter;
import com.researchmatch.config.AdapterRegistry;
import com.researchmatch.config.DuplicateEntityException;
import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.matching.MatchingEngine;
import com.researchmatch.models.Application;
import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.SkillEvidence;
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
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Member 4's facade: wiring, delegation, validation and the privacy rule. Member 2/3
 * collaborators are mocked so unfinished business logic never leaks into these tests.
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

    private StudentProfile student() {
        StudentProfile s = new StudentProfile();
        s.setId(7L);
        s.setName("Ada");
        s.setEmail("ada@example.com");
        s.setSearchableByFaculty(true);
        return s;
    }

    // ---------------------------------------------------------------- wiring

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

    // ---------------------------------------------------------------- students

    @Test
    void findStudentNeverThrows() {
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student()));
        assertThat(facade.findStudent(7L)).isPresent();
        assertThat(facade.findStudent(null)).isEmpty();
        assertThat(facade.findStudent(99L)).isEmpty();
    }

    @Test
    void getStudentThrowsWhenMissing() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.getStudent(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void saveStudentRejectsNullAndBlankFields() {
        assertThatThrownBy(() -> facade.saveStudent(null)).isInstanceOf(InvalidRequestException.class);

        StudentProfile blankName = student();
        blankName.setName("  ");
        assertThatThrownBy(() -> facade.saveStudent(blankName)).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("name");

        StudentProfile blankEmail = student();
        blankEmail.setEmail("");
        assertThatThrownBy(() -> facade.saveStudent(blankEmail)).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("mail");
    }

    @Test
    void saveStudentRejectsOutOfRangeHours() {
        StudentProfile tooFew = student();
        tooFew.setHoursPerWeek(0);
        when(studentRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.saveStudent(tooFew)).isInstanceOf(InvalidRequestException.class);

        StudentProfile tooMany = student();
        tooMany.setHoursPerWeek(999);
        assertThatThrownBy(() -> facade.saveStudent(tooMany)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void saveStudentRejectsDuplicateEmailFromAnotherStudent() {
        StudentProfile incoming = student();
        StudentProfile existing = new StudentProfile();
        existing.setId(1L);
        existing.setEmail("ada@example.com");
        when(studentRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> facade.saveStudent(incoming))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining("already");
    }

    @Test
    void saveStudentAllowsSameEmailOnUpdate() {
        StudentProfile incoming = student();
        StudentProfile existing = new StudentProfile();
        existing.setId(7L);
        existing.setEmail("ada@example.com");
        when(studentRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        StudentProfile saved = facade.saveStudent(incoming);
        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getName()).isEqualTo("Ada");
    }

    @Test
    void saveStudentPreservesPrivacyDefault() {
        StudentProfile incoming = new StudentProfile();
        incoming.setId(7L);
        incoming.setName("Ada");
        incoming.setEmail("ada@example.com");
        assertThat(incoming.isSearchableByFaculty()).isFalse();
        when(studentRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
        when(studentRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        StudentProfile saved = facade.saveStudent(incoming);
        assertThat(saved.isSearchableByFaculty()).isFalse();
    }

    // ---------------------------------------------------------------- skills

    @Test
    void importSkillsPersistsEvidenceAndPromotesVerifiedSkills() {
        ResearchDataAdapter adapter = Mockito.mock(ResearchDataAdapter.class);
        when(adapter.platformName()).thenReturn("github");
        when(adapter.importEvidence("octocat")).thenReturn(List.of(
                new ImportedEvidence("Java", "repo-a", "snippet", 0.9),
                new ImportedEvidence("Python", "repo-b", null, 0.7)));
        ResearchMatchFacade custom = new ResearchMatchFacade(studentRepository, facultyRepository,
                opportunityRepository, applicationRepository, skillEvidenceRepository, matchingEngine,
                recruitmentPipeline, new AdapterRegistry(List.of(adapter)));

        when(studentRepository.findById(7L)).thenReturn(Optional.of(student()));
        when(skillEvidenceRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(studentRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        List<SkillEvidence> imported = custom.importSkills(7L, "github", "octocat");

        assertThat(imported).extracting(SkillEvidence::getSkillName).containsExactly("Java", "Python");
        assertThat(imported).allMatch(SkillEvidence::isVerifiedByStudent);

        ArgumentCaptor<StudentProfile> captor = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getVerifiedSkills()).contains("Java", "Python");
    }

    @Test
    void importSkillsKeepsSampleDataUnverifiedAndOutOfVerifiedSkills() {
        ResearchDataAdapter offline = new GitHubSkillAdapter("", null);
        ResearchMatchFacade custom = new ResearchMatchFacade(studentRepository, facultyRepository,
                opportunityRepository, applicationRepository, skillEvidenceRepository, matchingEngine,
                recruitmentPipeline, new AdapterRegistry(List.of(offline)));

        when(studentRepository.findById(7L)).thenReturn(Optional.of(student()));
        when(skillEvidenceRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(studentRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        List<SkillEvidence> imported = custom.importSkills(7L, "github", "demo-mei-ling-ho");

        // The demo path works with no token...
        assertThat(imported).extracting(SkillEvidence::getSkillName).containsExactly("Python", "Jupyter Notebook");
        // ...but the rows are flagged, not presented as verified evidence...
        assertThat(imported).noneMatch(SkillEvidence::isVerifiedByStudent);
        assertThat(imported).allSatisfy(
                row -> assertThat(row.getRepoName()).startsWith(ResearchDataAdapter.FIXTURE_SOURCE_PREFIX));
        // ...and they never become verified skills on the profile.
        ArgumentCaptor<StudentProfile> captor = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getVerifiedSkills()).doesNotContain("Python", "Jupyter Notebook");
    }

    @Test
    void importSkillsMergesIntoExistingVerifiedSkillsAndSkipsBlankNames() {
        // A preexisting verified skill survives; a blank-named live row must not create a verified entry.
        StudentProfile existing = student();
        existing.setVerifiedSkills(new java.util.LinkedHashSet<>(List.of("Figma")));
        ResearchDataAdapter adapter = Mockito.mock(ResearchDataAdapter.class);
        when(adapter.platformName()).thenReturn("github");
        when(adapter.importEvidence("live")).thenReturn(List.of(
                new ImportedEvidence("Java", "repo-a", "snippet", 0.9),
                new ImportedEvidence("Spring", "repo-b", "snippet", 0.8)));
        ResearchMatchFacade custom = new ResearchMatchFacade(studentRepository, facultyRepository,
                opportunityRepository, applicationRepository, skillEvidenceRepository, matchingEngine,
                recruitmentPipeline, new AdapterRegistry(List.of(adapter)));
        when(studentRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(skillEvidenceRepository.saveAll(anyList())).thenAnswer(i -> {
            @SuppressWarnings("unchecked")
            List<SkillEvidence> rows = new ArrayList<>((List<SkillEvidence>) i.getArgument(0));
            rows.add(new SkillEvidence(null, 7L, "  ", "repo-c", "blank name"));   // blank must be skipped
            return rows;
        });
        when(studentRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        custom.importSkills(7L, "github", "live");

        ArgumentCaptor<StudentProfile> captor = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getVerifiedSkills()).containsExactly("Figma", "Java", "Spring");
    }

    @Test
    void importSkillsRejectsUnknownSourceAndBlankHandle() {
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student()));

        assertThatThrownBy(() -> facade.importSkills(7L, null, "octocat"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> facade.importSkills(7L, "unknown", "octocat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown import source");
        assertThatThrownBy(() -> facade.importSkills(7L, "github", "  "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("handle");
    }

    @Test
    void importSkillsFailsForUnknownStudent() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.importSkills(99L, "github", "octocat"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void evidenceForReturnsEmptyWhenRepositoryReturnsNull() {
        when(skillEvidenceRepository.findByStudentId(7L)).thenReturn(null);
        assertThat(facade.evidenceFor(7L)).isEmpty();
    }

    @Test
    void evidenceForReturnsRows() {
        SkillEvidence ev = new SkillEvidence(1L, 7L, "Java", "repo", "snip");
        when(skillEvidenceRepository.findByStudentId(7L)).thenReturn(List.of(ev));
        assertThat(facade.evidenceFor(7L)).containsExactly(ev);
    }

    @Test
    void toEvidenceMarksFixtureSourcesUnverified() {
        SkillEvidence row = facade.toEvidence(7L,
                List.of(new ImportedEvidence("Java", ResearchDataAdapter.FIXTURE_SOURCE_PREFIX + "demo/x",
                        ResearchDataAdapter.OFFLINE_SNIPPET_PREFIX + "sample", 0.5))).get(0);

        assertThat(row.isVerifiedByStudent()).isFalse();
    }

    // ---------------------------------------------------------------- opportunities + faculty

    @Test
    void getOpportunityThrowsForNullOrMissing() {
        assertThatThrownBy(() -> facade.getOpportunity(null)).isInstanceOf(ResourceNotFoundException.class);
        when(opportunityRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.getOpportunity(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void saveOpportunityValidatesFaculty() {
        assertThatThrownBy(() -> facade.saveOpportunity(null)).isInstanceOf(InvalidRequestException.class);

        Opportunity noFaculty = new Opportunity();
        assertThatThrownBy(() -> facade.saveOpportunity(noFaculty)).isInstanceOf(InvalidRequestException.class);

        Opportunity opportunity = new Opportunity();
        opportunity.setFacultyId(3L);
        opportunity.setTitle("NLP");
        when(facultyRepository.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.saveOpportunity(opportunity))
                .isInstanceOf(ResourceNotFoundException.class);

        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        when(opportunityRepository.save(opportunity)).thenReturn(opportunity);
        assertThat(facade.saveOpportunity(opportunity)).isSameAs(opportunity);
    }

    @Test
    void opportunitiesForFacultyHandlesNull() {
        when(opportunityRepository.findByFacultyId(1L)).thenReturn(null);
        assertThat(facade.opportunitiesForFaculty(1L)).isEmpty();
        Opportunity op = new Opportunity(1L, 1L, "t", "d");
        when(opportunityRepository.findByFacultyId(2L)).thenReturn(List.of(op));
        assertThat(facade.opportunitiesForFaculty(2L)).containsExactly(op);
    }

    @Test
    void getFacultyThrowsForNullOrMissing() {
        assertThatThrownBy(() -> facade.getFaculty(null)).isInstanceOf(ResourceNotFoundException.class);
        when(facultyRepository.findById(4L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.getFaculty(4L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- matching delegation

    @Test
    void matchDelegatesToEngine() {
        MatchResult result = new MatchResult(1L, 7L, 2L);
        when(matchingEngine.evaluate(7L, 2L)).thenReturn(result);
        assertThat(facade.match(7L, 2L)).isSameAs(result);
    }

    @Test
    void recommendDelegatesAndHandlesNull() {
        MatchResult result = new MatchResult(1L, 7L, 2L);
        when(matchingEngine.rankOpportunities(7L, "balanced")).thenReturn(List.of(result));
        assertThat(facade.recommend(7L, "balanced")).containsExactly(result);

        when(matchingEngine.rankOpportunities(7L, null)).thenReturn(null);
        assertThat(facade.recommend(7L, null)).isEmpty();
    }

    // ---------------------------------------------------------------- applications

    @Test
    void submitApplicationRejectsNullPair() {
        assertThatThrownBy(() -> facade.submitApplication(null, 1L, "s"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> facade.submitApplication(1L, null, "s"))
                .isInstanceOf(InvalidRequestException.class);
        verify(recruitmentPipeline, never()).submit(anyLong(), anyLong(), anyString());
    }

    @Test
    void submitApplicationDelegates() {
        Application app = new Application(1L, 7L, 2L);
        when(recruitmentPipeline.submit(7L, 2L, "hi")).thenReturn(app);
        assertThat(facade.submitApplication(7L, 2L, "hi")).isSameAs(app);
    }

    @Test
    void applicationsForStudentHandlesNull() {
        when(applicationRepository.findByStudentId(7L)).thenReturn(null);
        assertThat(facade.applicationsForStudent(7L)).isEmpty();
        Application app = new Application(1L, 7L, 2L);
        when(applicationRepository.findByStudentId(8L)).thenReturn(List.of(app));
        assertThat(facade.applicationsForStudent(8L)).containsExactly(app);
    }

    @Test
    void transitionsDelegateToPipeline() {
        Application app = new Application(1L, 7L, 2L);
        when(recruitmentPipeline.shortlist(1L)).thenReturn(app);
        when(recruitmentPipeline.scheduleInterview(1L)).thenReturn(app);
        when(recruitmentPipeline.accept(1L)).thenReturn(app);
        when(recruitmentPipeline.reject(1L)).thenReturn(app);

        assertThat(facade.shortlist(1L)).isSameAs(app);
        assertThat(facade.scheduleInterview(1L)).isSameAs(app);
        assertThat(facade.accept(1L)).isSameAs(app);
        assertThat(facade.reject(1L)).isSameAs(app);
    }

    // ---------------------------------------------------------------- privacy in discoverCandidates

    @Test
    void discoverCandidatesThrowsForMissingFaculty() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.discoverCandidates(3L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void discoverCandidatesEmptyWhenNoSearchableStudents() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        when(studentRepository.findBySearchableByFacultyTrue()).thenReturn(List.of());
        assertThat(facade.discoverCandidates(3L)).isEmpty();
        // Null list path is also tolerated.
        when(studentRepository.findBySearchableByFacultyTrue()).thenReturn(null);
        assertThat(facade.discoverCandidates(3L)).isEmpty();
    }

    @Test
    void discoverCandidatesExcludesNonSearchableEvenIfTheyApplied() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        StudentProfile searchable = student();
        StudentProfile privateStudent = new StudentProfile();
        privateStudent.setId(8L);
        privateStudent.setName("Private");
        privateStudent.setEmail("private@example.com");
        privateStudent.setSearchableByFaculty(false);  // default

        when(studentRepository.findBySearchableByFacultyTrue()).thenReturn(List.of(searchable));

        Opportunity op = new Opportunity(10L, 3L, "t", "d");
        when(opportunityRepository.findByFacultyId(3L)).thenReturn(List.of(op));
        // Private student applied; searchable one did too.
        when(applicationRepository.findByOpportunityId(10L)).thenReturn(List.of(
                new Application(1L, 8L, 10L),
                new Application(2L, 7L, 10L)));

        List<StudentProfile> candidates = facade.discoverCandidates(3L);
        assertThat(candidates).extracting(StudentProfile::getId).containsExactly(7L);
    }

    @Test
    void discoverCandidatesFallsBackToAllSearchableWhenNoApplicants() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        StudentProfile searchable = student();
        when(studentRepository.findBySearchableByFacultyTrue()).thenReturn(List.of(searchable));

        Opportunity op = new Opportunity(10L, 3L, "t", "d");
        when(opportunityRepository.findByFacultyId(3L)).thenReturn(List.of(op));
        when(applicationRepository.findByOpportunityId(10L)).thenReturn(List.of());

        assertThat(facade.discoverCandidates(3L)).extracting(StudentProfile::getId).containsExactly(7L);
    }

    @Test
    void discoverCandidatesWithNoOpportunitiesReturnsSearchable() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        StudentProfile searchable = student();
        when(studentRepository.findBySearchableByFacultyTrue()).thenReturn(List.of(searchable));
        when(opportunityRepository.findByFacultyId(3L)).thenReturn(List.of());

        assertThat(facade.discoverCandidates(3L)).containsExactly(searchable);
    }

    @Test
    void discoverCandidatesSkipsNullsAndMissingIds() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        StudentProfile noId = new StudentProfile();
        noId.setSearchableByFaculty(true);
        when(studentRepository.findBySearchableByFacultyTrue())
                .thenReturn(java.util.Arrays.asList(null, noId));
        assertThat(facade.discoverCandidates(3L)).isEmpty();
    }

    @Test
    void discoverCandidatesHandlesNullApplicationsAndOpportunityIds() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        StudentProfile searchable = student();
        when(studentRepository.findBySearchableByFacultyTrue()).thenReturn(List.of(searchable));

        Opportunity noId = new Opportunity();
        Opportunity withId = new Opportunity(10L, 3L, "t", "d");
        when(opportunityRepository.findByFacultyId(3L)).thenReturn(java.util.Arrays.asList(null, noId, withId));
        when(applicationRepository.findByOpportunityId(10L)).thenReturn(null);

        assertThat(facade.discoverCandidates(3L)).containsExactly(searchable);
    }

    @Test
    void saveStudentAcceptsValidHoursBoundary() {
        StudentProfile one = student();
        one.setHoursPerWeek(1);
        StudentProfile max = student();
        max.setHoursPerWeek(ResearchMatchFacade.MAX_HOURS_PER_WEEK);
        when(studentRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
        when(studentRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(facade.saveStudent(one).getHoursPerWeek()).isEqualTo(1);
        assertThat(facade.saveStudent(max).getHoursPerWeek()).isEqualTo(ResearchMatchFacade.MAX_HOURS_PER_WEEK);
    }

    @Test
    void saveStudentRejectsDuplicateEmailForNewStudentWithNullId() {
        StudentProfile incoming = student();
        incoming.setId(null);
        StudentProfile existing = new StudentProfile();
        existing.setId(1L);
        existing.setEmail("ada@example.com");
        when(studentRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> facade.saveStudent(incoming))
                .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    void saveStudentRejectsNullName() {
        StudentProfile incoming = student();
        incoming.setName(null);
        assertThatThrownBy(() -> facade.saveStudent(incoming))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("name");
    }

    @Test
    void importSkillsHandlesNullVerifiedSkills() {
        ResearchDataAdapter adapter = Mockito.mock(ResearchDataAdapter.class);
        when(adapter.platformName()).thenReturn("github");
        when(adapter.importEvidence("octocat"))
                .thenReturn(List.of(new ImportedEvidence("Java", "repo", "snip", 0.5)));
        ResearchMatchFacade custom = new ResearchMatchFacade(studentRepository, facultyRepository,
                opportunityRepository, applicationRepository, skillEvidenceRepository, matchingEngine,
                recruitmentPipeline, new AdapterRegistry(List.of(adapter)));

        StudentProfile target = student();
        target.setVerifiedSkills(null);
        when(studentRepository.findById(7L)).thenReturn(Optional.of(target));
        when(skillEvidenceRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(studentRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        List<SkillEvidence> imported = custom.importSkills(7L, "github", "octocat");

        assertThat(imported).extracting(SkillEvidence::getSkillName).containsExactly("Java");
        verify(studentRepository).save(any(StudentProfile.class));
        assertThat(target.getVerifiedSkills()).contains("Java");
    }

    @Test
    void importSkillsSkipsBlankSkillNames() {
        ResearchDataAdapter adapter = Mockito.mock(ResearchDataAdapter.class);
        when(adapter.platformName()).thenReturn("github");
        when(adapter.importEvidence("octocat"))
                .thenReturn(List.of(new ImportedEvidence("Java", "repo", "snip", 0.5)));
        ResearchMatchFacade custom = new ResearchMatchFacade(studentRepository, facultyRepository,
                opportunityRepository, applicationRepository, skillEvidenceRepository, matchingEngine,
                recruitmentPipeline, new AdapterRegistry(List.of(adapter)));

        StudentProfile target = student();
        SkillEvidence blank = new SkillEvidence(1L, 7L, "  ", "r", "s");
        SkillEvidence java = new SkillEvidence(2L, 7L, "Java", "r", "s");
        java.setVerifiedByStudent(true);
        when(studentRepository.findById(7L)).thenReturn(Optional.of(target));
        when(skillEvidenceRepository.saveAll(anyList())).thenReturn(List.of(blank, java));
        when(studentRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        custom.importSkills(7L, "github", "octocat");

        assertThat(target.getVerifiedSkills()).containsExactly("Java");
    }

    @Test
    void discoverCandidatesHandlesNullOpportunityList() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        StudentProfile searchable = student();
        when(studentRepository.findBySearchableByFacultyTrue()).thenReturn(List.of(searchable));
        when(opportunityRepository.findByFacultyId(3L)).thenReturn(null);

        assertThat(facade.discoverCandidates(3L)).containsExactly(searchable);
    }

    @Test
    void discoverCandidatesSkipsNullApplicationAndNullStudentId() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        StudentProfile searchable = student();
        when(studentRepository.findBySearchableByFacultyTrue()).thenReturn(List.of(searchable));

        Opportunity op = new Opportunity(10L, 3L, "t", "d");
        when(opportunityRepository.findByFacultyId(3L)).thenReturn(List.of(op));
        when(applicationRepository.findByOpportunityId(10L)).thenReturn(java.util.Arrays.asList(
                null,
                new Application(1L, null, 10L),
                new Application(2L, 7L, 10L)));

        assertThat(facade.discoverCandidates(3L)).extracting(StudentProfile::getId).containsExactly(7L);
    }

    @Test
    void discoverCandidatesFiltersUnappliedAndNullCandidatesInApplicantBranch() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        StudentProfile noId = new StudentProfile();
        noId.setSearchableByFaculty(true);
        StudentProfile applied = student();
        StudentProfile notApplied = new StudentProfile();
        notApplied.setId(9L);
        notApplied.setSearchableByFaculty(true);
        when(studentRepository.findBySearchableByFacultyTrue())
                .thenReturn(java.util.Arrays.asList(null, noId, applied, notApplied));

        Opportunity op = new Opportunity(10L, 3L, "t", "d");
        when(opportunityRepository.findByFacultyId(3L)).thenReturn(List.of(op));
        when(applicationRepository.findByOpportunityId(10L)).thenReturn(List.of(new Application(1L, 7L, 10L)));

        assertThat(facade.discoverCandidates(3L)).extracting(StudentProfile::getId).containsExactly(7L);
    }

    @Test
    void discoverCandidatesFiltersOutNonSearchableAndNullsInFallback() {
        when(facultyRepository.findById(3L)).thenReturn(Optional.of(new FacultyProfile()));
        StudentProfile nonSearchable = Mockito.mock(StudentProfile.class);
        when(nonSearchable.isSearchableByFaculty()).thenReturn(false);
        when(nonSearchable.getId()).thenReturn(50L);
        StudentProfile noId = new StudentProfile();
        noId.setSearchableByFaculty(true);
        StudentProfile valid = student();
        when(studentRepository.findBySearchableByFacultyTrue())
                .thenReturn(java.util.Arrays.asList(null, nonSearchable, noId, valid));
        when(opportunityRepository.findByFacultyId(3L)).thenReturn(List.of());

        assertThat(facade.discoverCandidates(3L)).containsExactly(valid);
    }

    @Test
    void toEvidenceSkipsNullEntries() {
        List<SkillEvidence> rows = facade.toEvidence(7L,
                java.util.Arrays.asList(null, new ImportedEvidence("Java", "repo", "snip", 0.5)));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSkillName()).isEqualTo("Java");
    }

    @Test
    void importSkillsRejectsNullHandle() {
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student()));
        assertThatThrownBy(() -> facade.importSkills(7L, "github", null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("handle");
    }

    @Test
    void importSkillsRejectsWhitespaceSource() {
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student()));
        assertThatThrownBy(() -> facade.importSkills(7L, "  ", "octocat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void importSkillsRejectsEmptySource() {
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student()));
        assertThatThrownBy(() -> facade.importSkills(7L, "", "octocat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void saveStudentRejectsNullEmail() {
        StudentProfile incoming = student();
        incoming.setEmail(null);
        assertThatThrownBy(() -> facade.saveStudent(incoming))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("mail");
    }

    @Test
    void importSkillsSkipsNullSkillNames() {
        ResearchDataAdapter adapter = Mockito.mock(ResearchDataAdapter.class);
        when(adapter.platformName()).thenReturn("github");
        when(adapter.importEvidence("octocat"))
                .thenReturn(List.of(new ImportedEvidence("Java", "repo", "snip", 0.5)));
        ResearchMatchFacade custom = new ResearchMatchFacade(studentRepository, facultyRepository,
                opportunityRepository, applicationRepository, skillEvidenceRepository, matchingEngine,
                recruitmentPipeline, new AdapterRegistry(List.of(adapter)));

        when(studentRepository.findById(7L)).thenReturn(Optional.of(student()));
        SkillEvidence nullSkill = new SkillEvidence(2L, 7L, null, "r", "s");
        SkillEvidence java = new SkillEvidence(1L, 7L, "Java", "r", "s");
        java.setVerifiedByStudent(true);
        when(skillEvidenceRepository.saveAll(anyList())).thenReturn(List.of(nullSkill, java));
        when(studentRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        custom.importSkills(7L, "github", "octocat");

        ArgumentCaptor<StudentProfile> captor = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getVerifiedSkills()).containsOnly("Java").doesNotContainNull();
    }

    @Test
    void importSourcesAreDistinct() {
        assertThat(facade.importSources()).doesNotHaveDuplicates();
        assertThat(new HashSetSize(facade.importSources()).size()).isEqualTo(2);
    }

    /** Tiny helper to assert size without importing extra collections for one call. */
    private record HashSetSize(List<String> values) {
        int size() {
            return Set.copyOf(values).size();
        }
    }
}
