package com.researchmatch.pipeline;

import com.researchmatch.config.IllegalStateTransitionException;
import com.researchmatch.models.Application.Status;

/**
 * Shared plumbing for the concrete {@link ApplicationState}s (Member 3).
 *
 * <p>Each subclass only overrides the transitions it allows; everything else is rejected with
 * {@link #deny(String)}, so the transition matrix has exactly one definition per state.
 */
abstract class AbstractApplicationState implements ApplicationState {

    @Override
    public ApplicationState shortlist() {
        throw deny("shortlist");
    }

    @Override
    public ApplicationState interview() {
        throw deny("interview");
    }

    @Override
    public ApplicationState accept() {
        throw deny("accept");
    }

    @Override
    public ApplicationState reject() {
        throw deny("reject");
    }

    /** Builds the exception for a disallowed transition; callers {@code throw} it, so the path is coverable. */
    protected IllegalStateTransitionException deny(String transition) {
        return new IllegalStateTransitionException(
                "Cannot " + transition + " an application currently " + status());
    }

    @Override
    public String describe() {
        return "Application is " + status();
    }
}
