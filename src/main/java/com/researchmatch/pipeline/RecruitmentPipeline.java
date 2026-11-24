package com.researchmatch.pipeline;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.models.Application;
import com.researchmatch.repository.ApplicationRepository;
import com.researchmatch.repository.OpportunityRepository;
import org.springframework.stereotype.Service;

/**
 * Public entry point for the recruitment lifecycle (Member 3).
 *
 * <p>Consumers: {@code ResearchMatchFacade} (Member 4), integration tests (Member 5).
 * Every transition must: load the application, let the current {@link ApplicationState} validate the move,
 * persist the new status, then {@link ApplicationSubject#notifyStatusChanged notify} observers exactly once.
 */
@Service
public class RecruitmentPipeline {

    private final ApplicationRepository applicationRepository;
    private final OpportunityRepository opportunityRepository;
    private final ApplicationStateFactory stateFactory;
    private final ApplicationSubject subject;

    public RecruitmentPipeline(ApplicationRepository applicationRepository,
                               OpportunityRepository opportunityRepository,
                               ApplicationStateFactory stateFactory,
                               ApplicationSubject subject) {
        this.applicationRepository = applicationRepository;
        this.opportunityRepository = opportunityRepository;
        this.stateFactory = stateFactory;
        this.subject = subject;
    }

    /**
     * Stores a new application (status NEW) and notifies observers.
     *
     * @return the persisted application, never null
     * @throws ResourceNotFoundException if the student or opportunity does not exist
     * @throws InvalidRequestException   if {@code statement} is longer than 4000 chars
     * TODO (Member 3)
     */
    public Application submit(Long studentId, Long opportunityId, String statement) {
        throw new UnsupportedOperationException("TODO (Member 3): RecruitmentPipeline.submit");
    }

    /**
     * NEW → SHORTLISTED.
     *
     * @throws IllegalStateTransitionException if not allowed from the current status
     * @throws ResourceNotFoundException       if the application does not exist
     * TODO (Member 3)
     */
    public Application shortlist(Long applicationId) {
        throw new UnsupportedOperationException("TODO (Member 3): RecruitmentPipeline.shortlist");
    }

    /**
     * SHORTLISTED → INTERVIEW.
     *
     * @throws IllegalStateTransitionException if not allowed from the current status
     * @throws ResourceNotFoundException       if the application does not exist
     * TODO (Member 3)
     */
    public Application scheduleInterview(Long applicationId) {
        throw new UnsupportedOperationException("TODO (Member 3): RecruitmentPipeline.scheduleInterview");
    }

    /**
     * INTERVIEW → ACCEPTED.
     *
     * @throws IllegalStateTransitionException if not allowed from the current status
     * @throws ResourceNotFoundException       if the application does not exist
     * TODO (Member 3)
     */
    public Application accept(Long applicationId) {
        throw new UnsupportedOperationException("TODO (Member 3): RecruitmentPipeline.accept");
    }

    /**
     * → REJECTED from NEW, SHORTLISTED or INTERVIEW.
     *
     * @throws IllegalStateTransitionException if not allowed from the current status
     * @throws ResourceNotFoundException       if the application does not exist
     * TODO (Member 3)
     */
    public Application reject(Long applicationId) {
        throw new UnsupportedOperationException("TODO (Member 3): RecruitmentPipeline.reject");
    }

    /**
     * Resolves the current state object for an application.
     *
     * @throws ResourceNotFoundException if the application does not exist
     * TODO (Member 3)
     */
    public ApplicationState stateOf(Long applicationId) {
        throw new UnsupportedOperationException("TODO (Member 3): RecruitmentPipeline.stateOf");
    }

    public ApplicationRepository getApplicationRepository() {
        return applicationRepository;
    }

    public OpportunityRepository getOpportunityRepository() {
        return opportunityRepository;
    }

    public ApplicationStateFactory getStateFactory() {
        return stateFactory;
    }

    public ApplicationSubject getSubject() {
        return subject;
    }
}
