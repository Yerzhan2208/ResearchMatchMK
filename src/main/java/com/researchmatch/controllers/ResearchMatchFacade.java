package com.researchmatch.controllers;

import com.researchmatch.adapters.ImportedEvidence;
import com.researchmatch.adapters.ResearchDataAdapter;
import com.researchmatch.config.ResearchDataSourceRegistry;
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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade (Member 4): the single entry point used by the web layer and by Member 5's end-to-end tests.
 *
 * <p>It owns no business rules — it looks entities up, delegates to {@link MatchingEngine} (Member 2),
 * {@link RecruitmentPipeline} (Member 3) and the {@link ResearchDataAdapter}s (Member 3), and persists
 * through the Member 1 repositories. Controllers must never call those collaborators directly.
 */
@Service
public class ResearchMatchFacade {

    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;
    private final OpportunityRepository opportunityRepository;
    private final ApplicationRepository applicationRepository;
    private final SkillEvidenceRepository skillEvidenceRepository;
    private final MatchingEngine matchingEngine;
    private final RecruitmentPipeline recruitmentPipeline;
    private final ResearchDataSourceRegistry adapterRegistry;

    public ResearchMatchFacade(StudentRepository studentRepository,
                               FacultyRepository facultyRepository,
                               OpportunityRepository opportunityRepository,
                               ApplicationRepository applicationRepository,
                               SkillEvidenceRepository skillEvidenceRepository,
                               MatchingEngine matchingEngine,
                               RecruitmentPipeline recruitmentPipeline,
                               ResearchDataSourceRegistry adapterRegistry) {
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.opportunityRepository = opportunityRepository;
        this.applicationRepository = applicationRepository;
        this.skillEvidenceRepository = skillEvidenceRepository;
        this.matchingEngine = matchingEngine;
        this.recruitmentPipeline = recruitmentPipeline;
        this.adapterRegistry = adapterRegistry;
    }

    // ---------------------------------------------------------------- students

    /**
     * Library-style lookup that never throws; used for controllers that render either a profile or a 404.
     *
     * <p>TODO (Member 4): implement the repository lookup (returns empty, never throws).
     */
    public java.util.Optional<StudentProfile> findStudent(Long studentId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.findStudent");
    }

    /**
     * @throws ResourceNotFoundException if the student does not exist
     * TODO (Member 4)
     */
    public StudentProfile getStudent(Long studentId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.getStudent");
    }

    /**
     * Persists a student. New students always default to {@code searchableByFaculty = false}; only an
     * explicit {@link StudentProfile#setSearchableByFaculty(boolean)} by the student may change that.
     *
     * @throws com.researchmatch.config.InvalidRequestException   if name/email are blank
     * @throws com.researchmatch.config.DuplicateEntityException if the e-mail already exists
     * TODO (Member 4)
     */
    public StudentProfile saveStudent(StudentProfile student) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.saveStudent");
    }

    // ---------------------------------------------------------------- skills

    /**
     * Imports skills for a student from an external source through the matching adapter.
     *
     * @param source      a {@code ResearchDataAdapter.platformName()}, e.g. {@code "github"}
     * @param handle      GitHub username or OpenAlex id
     * @return the persisted evidence rows, never null
     * @throws com.researchmatch.config.InvalidRequestException if the source is unknown or blank
     * @throws ResourceNotFoundException                       if the student does not exist
     * TODO (Member 4)
     */
    public List<SkillEvidence> importSkills(Long studentId, String source, String handle) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.importSkills");
    }

    /** Evidence rows for a student, newest first. TODO (Member 4) */
    public List<SkillEvidence> evidenceFor(Long studentId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.evidenceFor");
    }

    // ---------------------------------------------------------------- opportunities

    /**
     * @throws ResourceNotFoundException if the opportunity does not exist
     * TODO (Member 4)
     */
    public Opportunity getOpportunity(Long opportunityId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.getOpportunity");
    }

    /**
     * Publishes an opportunity produced by {@code OpportunityBuilder}.
     *
     * @throws ResourceNotFoundException if {@code facultyId} does not exist
     * TODO (Member 4)
     */
    public Opportunity saveOpportunity(Opportunity opportunity) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.saveOpportunity");
    }

    /** All opportunities of a faculty member, never null. TODO (Member 4) */
    public List<Opportunity> opportunitiesForFaculty(Long facultyId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.opportunitiesForFaculty");
    }

    /** Faculty profile with inferred research areas, backing the public faculty page. TODO (Member 4) */
    public FacultyProfile getFaculty(Long facultyId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.getFaculty");
    }

    // ---------------------------------------------------------------- matching

    /**
     * Delegates to {@link MatchingEngine#evaluate(Long, Long)}.
     *
     * @throws ResourceNotFoundException if either side does not exist
     * TODO (Member 4)
     */
    public MatchResult match(Long studentId, Long opportunityId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.match");
    }

    /**
     * Delegates to {@link MatchingEngine#rankOpportunities(Long, String)}.
     *
     * @param strategyName one of {@code BalancedMatchStrategy.NAME}, {@code BeginnerFriendlyStrategy.NAME},
     *                     {@code SkillStrictStrategy.NAME}
     * TODO (Member 4)
     */
    public List<MatchResult> recommend(Long studentId, String strategyName) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.recommend");
    }

    // ---------------------------------------------------------------- applications

    /**
     * Delegates to {@link RecruitmentPipeline#submit(Long, Long, String)}.
     *
     * @throws com.researchmatch.config.InvalidRequestException if the pair was already applied for
     * TODO (Member 4)
     */
    public Application submitApplication(Long studentId, Long opportunityId, String statement) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.submitApplication");
    }

    /** Applications of a student, never null. TODO (Member 4) */
    public List<Application> applicationsForStudent(Long studentId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.applicationsForStudent");
    }

    /**
     * Candidate discovery for faculty: students who applied to one of the faculty's opportunities.
     * MUST respect {@link StudentProfile#isSearchableByFaculty()} — non-searchable students are never listed.
     *
     * @return never null; empty when the faculty owns no opportunities
     * @throws ResourceNotFoundException if the faculty does not exist
     * TODO (Member 4)
     */
    public List<StudentProfile> discoverCandidates(Long facultyId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.discoverCandidates");
    }

    /**
     * Faculty-side transitions; each delegates to {@link RecruitmentPipeline}.
     *
     * @throws com.researchmatch.config.IllegalStateTransitionException for a disallowed move
     * TODO (Member 4)
     */
    public Application shortlist(Long applicationId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.shortlist");
    }

    /** See {@link #shortlist(Long)}. TODO (Member 4) */
    public Application scheduleInterview(Long applicationId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.scheduleInterview");
    }

    /** See {@link #shortlist(Long)}. TODO (Member 4) */
    public Application accept(Long applicationId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.accept");
    }

    /** See {@link #shortlist(Long)}. TODO (Member 4) */
    public Application reject(Long applicationId) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.reject");
    }

    // ---------------------------------------------------------------- accessors (no logic)

    public StudentRepository getStudentRepository() {
        return studentRepository;
    }

    public FacultyRepository getFacultyRepository() {
        return facultyRepository;
    }

    public OpportunityRepository getOpportunityRepository() {
        return opportunityRepository;
    }

    public ApplicationRepository getApplicationRepository() {
        return applicationRepository;
    }

    public SkillEvidenceRepository getSkillEvidenceRepository() {
        return skillEvidenceRepository;
    }

    public MatchingEngine getMatchingEngine() {
        return matchingEngine;
    }

    public RecruitmentPipeline getRecruitmentPipeline() {
        return recruitmentPipeline;
    }

    public ResearchDataSourceRegistry getAdapterRegistry() {
        return adapterRegistry;
    }

    /** Available import sources, for the UI drop-down. Never null. */
    public List<String> importSources() {
        return adapterRegistry.sources().stream().map(ResearchDataAdapter::platformName).toList();
    }

    /** TODO (Member 4): used by {@link #importSkills(Long, String, String)}. */
    protected List<SkillEvidence> toEvidence(Long studentId, List<ImportedEvidence> imported) {
        throw new UnsupportedOperationException("TODO (Member 4): ResearchMatchFacade.toEvidence");
    }
}
