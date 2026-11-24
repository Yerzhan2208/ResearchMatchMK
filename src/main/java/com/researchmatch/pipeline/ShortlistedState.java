package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

/**
 * Faculty shortlisted the candidate. Allowed: INTERVIEW, REJECTED.
 *
 * <p>TODO (Member 3): implement the allowed transitions.
 */
@Component
public class ShortlistedState extends AbstractApplicationState {

    @Override
    public Application.Status status() {
        return Application.Status.SHORTLISTED;
    }

    @Override
    public ApplicationState interview() {
        throw new UnsupportedOperationException("TODO (Member 3): ShortlistedState.interview");
    }

    @Override
    public ApplicationState reject() {
        throw new UnsupportedOperationException("TODO (Member 3): ShortlistedState.reject");
    }

    @Override
    public String describe() {
        throw new UnsupportedOperationException("TODO (Member 3): ShortlistedState.describe");
    }
}
