package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

/**
 * Terminal state: the candidate joined the project. No further transitions.
 *
 * <p>TODO (Member 3): implement {@link #describe()}; every transition must keep throwing via
 * {@code super.*()}.
 */
@Component
public class AcceptedState extends AbstractApplicationState {

    @Override
    public Application.Status status() {
        return Application.Status.ACCEPTED;
    }

    @Override
    public String describe() {
        throw new UnsupportedOperationException("TODO (Member 3): AcceptedState.describe");
    }
}
