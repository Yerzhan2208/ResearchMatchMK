package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

/**
 * Initial state: a freshly submitted application. Allowed: SHORTLISTED, REJECTED.
 *
 * <p>TODO (Member 3): implement the allowed transitions; leave {@code super.*()} for rejected ones.
 */
@Component
public class NewState extends AbstractApplicationState {

    @Override
    public Application.Status status() {
        return Application.Status.NEW;
    }

    @Override
    public ApplicationState shortlist() {
        throw new UnsupportedOperationException("TODO (Member 3): NewState.shortlist");
    }

    @Override
    public ApplicationState reject() {
        throw new UnsupportedOperationException("TODO (Member 3): NewState.reject");
    }

    @Override
    public String describe() {
        throw new UnsupportedOperationException("TODO (Member 3): NewState.describe");
    }
}
