package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

/**
 * Interview state: the candidate is being interviewed. Allowed: ACCEPTED, REJECTED.
 */
@Component
public class InterviewState extends AbstractApplicationState {

    private final AcceptedState accepted;
    private final RejectedState rejected;

    public InterviewState(AcceptedState accepted, RejectedState rejected) {
        this.accepted = accepted;
        this.rejected = rejected;
    }

    @Override
    public Application.Status status() {
        return Application.Status.INTERVIEW;
    }

    @Override
    public ApplicationState accept() {
        return accepted;
    }

    @Override
    public ApplicationState reject() {
        return rejected;
    }

    @Override
    public String describe() {
        return "Application is at the interview stage; a final decision is pending.";
    }
}
