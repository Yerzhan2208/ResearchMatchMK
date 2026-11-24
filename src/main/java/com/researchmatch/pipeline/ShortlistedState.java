package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

/**
 * Shortlisted state: the faculty wants to hear more. Allowed: INTERVIEW, REJECTED.
 */
@Component
public class ShortlistedState extends AbstractApplicationState {

    private final InterviewState interview;
    private final RejectedState rejected;

    public ShortlistedState(InterviewState interview, RejectedState rejected) {
        this.interview = interview;
        this.rejected = rejected;
    }

    @Override
    public Application.Status status() {
        return Application.Status.SHORTLISTED;
    }

    @Override
    public ApplicationState interview() {
        return interview;
    }

    @Override
    public ApplicationState reject() {
        return rejected;
    }

    @Override
    public String describe() {
        return "Application is shortlisted; schedule an interview to continue.";
    }
}
