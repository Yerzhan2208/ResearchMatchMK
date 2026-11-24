package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

/**
 * Candidate invited to interview. Allowed: ACCEPTED, REJECTED.
 *
 * <p>TODO (Member 3): implement the allowed transitions.
 */
@Component
public class InterviewState extends AbstractApplicationState {

    @Override
    public Application.Status status() {
        return Application.Status.INTERVIEW;
    }

    @Override
    public ApplicationState accept() {
        throw new UnsupportedOperationException("TODO (Member 3): InterviewState.accept");
    }

    @Override
    public ApplicationState reject() {
        throw new UnsupportedOperationException("TODO (Member 3): InterviewState.reject");
    }

    @Override
    public String describe() {
        throw new UnsupportedOperationException("TODO (Member 3): InterviewState.describe");
    }
}
