package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

/**
 * Initial state: a freshly submitted application. Allowed: SHORTLISTED, REJECTED.
 *
 * <p>SHORTLISTED and INTERVIEW are rejected through {@link AbstractApplicationState}'s deny-by-default.
 */
@Component
public class NewState extends AbstractApplicationState {

    private final ShortlistedState shortlisted;
    private final RejectedState rejected;

    public NewState(ShortlistedState shortlisted, RejectedState rejected) {
        this.shortlisted = shortlisted;
        this.rejected = rejected;
    }

    @Override
    public Application.Status status() {
        return Application.Status.NEW;
    }

    @Override
    public ApplicationState shortlist() {
        return shortlisted;
    }

    @Override
    public ApplicationState reject() {
        return rejected;
    }

    @Override
    public String describe() {
        return "Application is new and waiting for the faculty to review it.";
    }
}
