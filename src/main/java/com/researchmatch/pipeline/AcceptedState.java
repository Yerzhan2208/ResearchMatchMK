package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

/**
 * Terminal state: the candidate was accepted. Every transition is rejected.
 */
@Component
public class AcceptedState extends AbstractApplicationState {

    @Override
    public Application.Status status() {
        return Application.Status.ACCEPTED;
    }

    @Override
    public String describe() {
        return "Application is accepted; this is the final state.";
    }
}
