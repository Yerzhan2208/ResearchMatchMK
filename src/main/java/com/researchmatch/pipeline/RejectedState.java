package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

/**
 * Terminal state: the application was rejected. Every transition is rejected.
 */
@Component
public class RejectedState extends AbstractApplicationState {

    @Override
    public Application.Status status() {
        return Application.Status.REJECTED;
    }

    @Override
    public String describe() {
        return "Application is rejected; this is the final state.";
    }
}
