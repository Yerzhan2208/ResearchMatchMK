package com.researchmatch.controllers;

import com.researchmatch.adapters.ImportedEvidence;
import com.researchmatch.adapters.ResearchDataAdapter;
import com.researchmatch.config.ResearchDataSourceRegistry;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /**
     * A student may not claim more than this many hours a week; a sanity ceiling that turns typos
     * (e.g. 400) into a validation error instead of an impossible profile.
     */
    static final int MAX_HOURS_PER_WEEK = 168;

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
     */
    public Optional<StudentProfile> findStudent(Long studentId) {
        if (studentId == null) {
            return Optional.empty();
        }
        return studentRepository.findById(studentId);
    }

    /**
     * @throws ResourceNotFoundException if the student does not exist
     */
    public StudentProfile getStudent(Long studentId) {
        return findStudent(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student " + studentId + " not found"));
    }

    /**
     * Persists a student. New students always default to {@code searchableByFaculty = false}; only an
     * explicit {@link StudentProfile#setSearchableByFaculty(boolean)} by the student may change that.
     *
     * @throws InvalidRequestException   if name/email are blank or hours are out of range
     * @throws DuplicateEntityException if the e-mail already belongs to a different student
     */
    public StudentProfile saveStudent(StudentProfile student) {
        if (student == null) {
            throw new InvalidRequestException("Student profile must not be null");
        }
        if (isBlank(student.getName())) {
            throw new InvalidRequestException("Student name is required");
        }
        if (isBlank(student.getEmail())) {
            throw new InvalidRequestException("Student e-mail is required");
        }
        Integer hours = student.getHoursPerWeek();
        if (hours != null && (hours <= 0 || hours > MAX_HOURS_PER_WEEK)) {
            throw new InvalidRequestException("hoursPerWeek must be between 1 and " + MAX_HOURS_PER_WEEK);
        }
        studentRepository.findByEmail(student.getEmail().trim()).ifPresent(existing -> {
            if (student.getId() == null || !existing.getId().equals(student.getId())) {
                throw new DuplicateEntityException("A student with e-mail " + student.getEmail() + " already exists");
            }
        });
        student.setName(student.getName().trim());
        student.setEmail(student.getEmail().trim());
        return studentRepository.save(student);
    }

    // ---------------------------------------------------------------- skills

    /**
     * Imports skills for a student from an external source through the matching adapter.
     *
     * <p>Rows produced from sample data (an adapter with no live lookup) are persisted with
     * {@code verifiedByStudent = false} and are <strong>not</strong> promoted into the student's
     * {@code verifiedSkills}; only rows backed by a real lookup are promoted.
     *
     * @param source a {@code ResearchDataAdapter.platformName()}, e.g. {@code "github"}
     * @param handle GitHub username or OpenAlex id
     * @return the persisted evidence rows, never null
     * @throws InvalidRequestException if the source is unknown or blank, or the handle is blank
     * @throws ResourceNotFoundException if the student does not exist
     */
    @Transactional
    public List<SkillEvidence> importSkills(Long studentId, String source, String handle) {
        StudentProfile student = getStudent(studentId);
        if (isBlank(handle)) {
            throw new InvalidRequestException("A profile handle is required for the import");
        }
        ResearchDataAdapter adapter = adapterRegistry.forSource(source)
                .orElseThrow(() -> new InvalidRequestException("Unknown import source: " + source));
        List<ImportedEvidence> imported = adapter.importEvidence(handle.trim());
        // Fixture entries (no live lookup) are sample data: stored so the demo shows something, flagged
        // unverified, and never promoted into verifiedSkills below.
        List<SkillEvidence> persisted = skillEvidenceRepository.saveAll(toEvidence(student.getId(), imported));

        // Promote genuinely imported skills to verified skills on the profile so matching can use them.
        Set<String> verified = student.getVerifiedSkills() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(student.getVerifiedSkills());
        persisted.stream()
                .filter(SkillEvidence::isVerifiedByStudent)
                .map(SkillEvidence::getSkillName)
                .filter(s -> !isBlank(s))
                .forEach(verified::add);
        student.setVerifiedSkills(verified);
        studentRepository.save(student);
        return persisted;
    }

    /** Evidence rows for a student, newest first (repository order preserved). Never null. */
    public List<SkillEvidence> evidenceFor(Long studentId) {
        List<SkillEvidence> rows = skillEvidenceRepository.findByStudentId(studentId);
        return rows == null ? List.of() : List.copyOf(rows);
    }

    // ---------------------------------------------------------------- opportunities

    /**
     * @throws ResourceNotFoundException if the opportunity does not exist
     */
    public Opportunity getOpportunity(Long opportunityId) {
        if (opportunityId == null) {
            throw new ResourceNotFoundException("Opportunity not found");
        }
        return opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity " + opportunityId + " not found"));
    }

    /**
     * Publishes an opportunity produced by {@code OpportunityBuilder}.
     *
     * @throws ResourceNotFoundException if {@code facultyId} does not exist
     * @throws InvalidRequestException   if the opportunity is null or its faculty id is missing
     */
    public Opportunity saveOpportunity(Opportunity opportunity) {
        if (opportunity == null) {
            throw new InvalidRequestException("Opportunity must not be null");
        }
        Long facultyId = opportunity.getFacultyId();
        if (facultyId == null) {
            throw new InvalidRequestException("Opportunity requires a faculty id");
        }
        if (facultyRepository.findById(facultyId).isEmpty()) {
            throw new ResourceNotFoundException("Faculty " + facultyId + " not found");
        }
        return opportunityRepository.save(opportunity);
    }

    /** All opportunities of a faculty member, never null. */
    public List<Opportunity> opportunitiesForFaculty(Long facultyId) {
        List<Opportunity> rows = opportunityRepository.findByFacultyId(facultyId);
        return rows == null ? List.of() : List.copyOf(rows);
    }

    /** Faculty profile, backing the public faculty page. */
    public FacultyProfile getFaculty(Long facultyId) {
        if (facultyId == null) {
            throw new ResourceNotFoundException("Faculty not found");
        }
        return facultyRepository.findById(facultyId)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty " + facultyId + " not found"));
    }

    // ---------------------------------------------------------------- matching

    /**
     * Delegates to {@link MatchingEngine#evaluate(Long, Long)}.
     *
     * @throws ResourceNotFoundException if either side does not exist
     */
    public MatchResult match(Long studentId, Long opportunityId) {
        return matchingEngine.evaluate(studentId, opportunityId);
    }

    /**
     * Delegates to {@link MatchingEngine#rankOpportunities(Long, String)}.
     *
     * @param strategyName one of {@code BalancedMatchStrategy.NAME}, {@code BeginnerFriendlyStrategy.NAME},
     *                     {@code SkillStrictStrategy.NAME}; null/blank defaults to balanced
     */
    public List<MatchResult> recommend(Long studentId, String strategyName) {
        List<MatchResult> ranked = matchingEngine.rankOpportunities(studentId, strategyName);
        return ranked == null ? List.of() : ranked;
    }

    // ---------------------------------------------------------------- applications

    /**
     * Delegates to {@link RecruitmentPipeline#submit(Long, Long, String)}.
     *
     * @throws InvalidRequestException if the pair was already applied for (checked before delegating)
     */
    public Application submitApplication(Long studentId, Long opportunityId, String statement) {
        // Guard only against a null pair; the "already applied" rule lives in the pipeline (Member 3).
        if (studentId == null || opportunityId == null) {
            throw new InvalidRequestException("Both a student and an opportunity are required to apply");
        }
        return recruitmentPipeline.submit(studentId, opportunityId, statement);
    }

    /** Applications of a student, never null. */
    public List<Application> applicationsForStudent(Long studentId) {
        List<Application> rows = applicationRepository.findByStudentId(studentId);
        return rows == null ? List.of() : List.copyOf(rows);
    }

    /**
     * Candidate discovery for faculty: every student the faculty can legitimately see.
     *
     * <p>The faculty's own opportunities define "legitimate", but the hard privacy rule is enforced here:
     * any student whose {@link StudentProfile#isSearchableByFaculty()} is {@code false} is filtered out,
     * regardless of whether they applied. The repository's privacy-aware query seeds the candidates and
     * applicants to the faculty's opportunities are intersected with the searchable set.
     *
     * @return never null; empty when the faculty owns no opportunities or no student opted in
     * @throws ResourceNotFoundException if the faculty does not exist
     */
    public List<StudentProfile> discoverCandidates(Long facultyId) {
        getFaculty(facultyId);

        List<StudentProfile> searchable = studentRepository.findBySearchableByFacultyTrue();
        if (searchable == null || searchable.isEmpty()) {
            return List.of();
        }
        Set<Long> searchableIds = new HashSet<>();
        for (StudentProfile candidate : searchable) {
            if (candidate != null && candidate.isSearchableByFaculty() && candidate.getId() != null) {
                searchableIds.add(candidate.getId());
            }
        }
        if (searchableIds.isEmpty()) {
            return List.of();
        }

        List<Opportunity> opportunities = opportunityRepository.findByFacultyId(facultyId);
        if (opportunities == null || opportunities.isEmpty()) {
            // A faculty with no opportunities may still browse opted-in students; show them all.
            return filterSearchable(searchable, searchableIds);
        }

        // Prefer students who applied to this faculty's opportunities; fall back to all opted-in students.
        Set<Long> applicantIds = new LinkedHashSet<>();
        Set<Long> opportunityIds = new HashSet<>();
        for (Opportunity opportunity : opportunities) {
            if (opportunity != null && opportunity.getId() != null) {
                opportunityIds.add(opportunity.getId());
            }
        }
        for (Long opportunityId : opportunityIds) {
            List<Application> applications = applicationRepository.findByOpportunityId(opportunityId);
            if (applications == null) {
                continue;
            }
            for (Application application : applications) {
                if (application != null && application.getStudentId() != null) {
                    applicantIds.add(application.getStudentId());
                }
            }
        }

        List<StudentProfile> result;
        if (applicantIds.isEmpty()) {
            result = filterSearchable(searchable, searchableIds);
        } else {
            result = new ArrayList<>();
            for (StudentProfile candidate : searchable) {
                if (candidate != null && candidate.getId() != null && applicantIds.contains(candidate.getId())) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    private static List<StudentProfile> filterSearchable(List<StudentProfile> source, Set<Long> searchableIds) {
        List<StudentProfile> filtered = new ArrayList<>();
        for (StudentProfile candidate : source) {
            if (candidate != null && candidate.isSearchableByFaculty()
                    && candidate.getId() != null && searchableIds.contains(candidate.getId())) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    /**
     * Faculty-side transitions; each delegates to {@link RecruitmentPipeline}.
     *
     * @throws com.researchmatch.config.IllegalStateTransitionException for a disallowed move
     */
    public Application shortlist(Long applicationId) {
        return recruitmentPipeline.shortlist(applicationId);
    }

    /** See {@link #shortlist(Long)}. */
    public Application scheduleInterview(Long applicationId) {
        return recruitmentPipeline.scheduleInterview(applicationId);
    }

    /** See {@link #shortlist(Long)}. */
    public Application accept(Long applicationId) {
        return recruitmentPipeline.accept(applicationId);
    }

    /** See {@link #shortlist(Long)}. */
    public Application reject(Long applicationId) {
        return recruitmentPipeline.reject(applicationId);
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

    /** Maps provider-neutral {@link ImportedEvidence} into persisted-ready {@link SkillEvidence} rows. */
    protected List<SkillEvidence> toEvidence(Long studentId, List<ImportedEvidence> imported) {
        if (imported == null || imported.isEmpty()) {
            return List.of();
        }
        List<SkillEvidence> rows = new ArrayList<>(imported.size());
        for (ImportedEvidence evidence : imported) {
            if (evidence == null) {
                continue;
            }
            SkillEvidence row = new SkillEvidence();
            row.setStudentId(studentId);
            row.setSkillName(evidence.skillName());
            row.setRepoName(evidence.sourceName());
            row.setEvidenceSnippet(evidence.evidenceSnippet());
            row.setVerifiedByStudent(!evidence.isSampleData());
            rows.add(row);
        }
        return rows;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
