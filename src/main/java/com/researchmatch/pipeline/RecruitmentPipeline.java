package com.researchmatch.pipeline;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.models.Application;
import com.researchmatch.repository.ApplicationRepository;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.UnaryOperator;

/**
 * Public entry point for the recruitment lifecycle (Member 3).
 *
 * <p>Consumers: {@code ResearchMatchFacade} (Member 4), integration tests (Member 5).
 * Every transition must: load the application, let the current {@link ApplicationState} validate the move,
 * persist the new status, then {@link ApplicationSubject#notifyStatusChanged notify} observers exactly once.
 */
@Service
public class RecruitmentPipeline {

    /** Longest application statement accepted, matching the {@code application.statement} column length. */
    public static final int MAX_STATEMENT_LENGTH = 4000;

    private final ApplicationRepository applicationRepository;
    private final OpportunityRepository opportunityRepository;
    private final StudentRepository studentRepository;
    private final ApplicationStateFactory stateFactory;
    private final ApplicationSubject subject;

    public RecruitmentPipeline(ApplicationRepository applicationRepository,
                               OpportunityRepository opportunityRepository,
                               StudentRepository studentRepository,
                               ApplicationStateFactory stateFactory,
                               ApplicationSubject subject) {
        this.applicationRepository = applicationRepository;
        this.opportunityRepository = opportunityRepository;
        this.studentRepository = studentRepository;
        this.stateFactory = stateFactory;
        this.subject = subject;
    }

    /**
     * Stores a new application (status NEW) and notifies observers.
     *
     * @return the persisted application, never null
     * @throws ResourceNotFoundException if the student or opportunity does not exist
     * @throws InvalidRequestException   if the pair was already applied for, or {@code statement} is longer
     *                                   than {@value #MAX_STATEMENT_LENGTH} chars
     */
    @Transactional
    public Application submit(Long studentId, Long opportunityId, String statement) {
        if (studentId == null || opportunityId == null) {
            throw new InvalidRequestException("Both a student and an opportunity are required to apply");
        }
        if (studentRepository.findById(studentId).isEmpty()) {
            throw new ResourceNotFoundException("Student " + studentId + " not found");
        }
        if (opportunityRepository.findById(opportunityId).isEmpty()) {
            throw new ResourceNotFoundException("Opportunity " + opportunityId + " not found");
        }
        if (statement != null && statement.length() > MAX_STATEMENT_LENGTH) {
            throw new InvalidRequestException(
                    "Statement must be " + MAX_STATEMENT_LENGTH + " characters or fewer");
        }
        // Recruitment rule: one application per (student, opportunity) pair.
        boolean alreadyApplied = applicationRepository.findByStudentId(studentId).stream()
                .anyMatch(existing -> opportunityId.equals(existing.getOpportunityId()));
        if (alreadyApplied) {
            throw new InvalidRequestException(
                    "Student " + studentId + " has already applied to opportunity " + opportunityId);
        }

        Application application = new Application();
        application.setStudentId(studentId);
        application.setOpportunityId(opportunityId);
        application.setStatement(statement);
        application.setStatus(Application.Status.NEW);
        Application saved = applicationRepository.save(application);
        subject.notifyStatusChanged(saved, Application.Status.NEW, Application.Status.NEW);
        return saved;
    }

    /**
     * NEW → SHORTLISTED.
     *
     * @throws com.researchmatch.config.IllegalStateTransitionException if not allowed from the current status
     * @throws ResourceNotFoundException                               if the application does not exist
     */
    @Transactional
    public Application shortlist(Long applicationId) {
        return transition(applicationId, ApplicationState::shortlist);
    }

    /**
     * SHORTLISTED → INTERVIEW.
     *
     * @throws com.researchmatch.config.IllegalStateTransitionException if not allowed from the current status
     * @throws ResourceNotFoundException                               if the application does not exist
     */
    @Transactional
    public Application scheduleInterview(Long applicationId) {
        return transition(applicationId, ApplicationState::interview);
    }

    /**
     * INTERVIEW → ACCEPTED.
     *
     * @throws com.researchmatch.config.IllegalStateTransitionException if not allowed from the current status
     * @throws ResourceNotFoundException                               if the application does not exist
     */
    @Transactional
    public Application accept(Long applicationId) {
        return transition(applicationId, ApplicationState::accept);
    }

    /**
     * → REJECTED from NEW, SHORTLISTED or INTERVIEW.
     *
     * @throws com.researchmatch.config.IllegalStateTransitionException if not allowed from the current status
     * @throws ResourceNotFoundException                               if the application does not exist
     */
    @Transactional
    public Application reject(Long applicationId) {
        return transition(applicationId, ApplicationState::reject);
    }

    /**
     * Resolves the current state object for an application.
     *
     * @throws ResourceNotFoundException if the application does not exist
     */
    public ApplicationState stateOf(Long applicationId) {
        return stateFactory.forStatus(load(applicationId).getStatus());
    }

    /**
     * Load → validate through the current state → persist → notify once.
     *
     * <p>The state validates before anything is written, so a disallowed move leaves the database untouched
     * and notifies nobody.
     */
    private Application transition(Long applicationId, UnaryOperator<ApplicationState> move) {
        Application application = load(applicationId);
        Application.Status from = application.getStatus();
        ApplicationState next = move.apply(stateFactory.forStatus(from));
        application.setStatus(next.status());
        Application saved = applicationRepository.save(application);
        subject.notifyStatusChanged(saved, from, saved.getStatus());
        return saved;
    }

    private Application load(Long applicationId) {
        if (applicationId == null) {
            throw new ResourceNotFoundException("Application id is required");
        }
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application " + applicationId + " not found"));
    }

    public ApplicationRepository getApplicationRepository() {
        return applicationRepository;
    }

    public OpportunityRepository getOpportunityRepository() {
        return opportunityRepository;
    }

    public StudentRepository getStudentRepository() {
        return studentRepository;
    }

    public ApplicationStateFactory getStateFactory() {
        return stateFactory;
    }

    public ApplicationSubject getSubject() {
        return subject;
    }
}
