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
        return deny("shortlist");
    }

    @Override
    public ApplicationState interview() {
        return deny("interview");
    }

    @Override
    public ApplicationState accept() {
        return deny("accept");
    }

    @Override
    public ApplicationState reject() {
        return deny("reject");
    }

    /** Always throws: this state does not allow the transition. */
    protected ApplicationState deny(String transition) {
        throw new IllegalStateTransitionException(
                "Cannot " + transition + " an application currently " + status());
    }

    @Override
    public String describe() {
        return "Application is " + status();
    }
}
