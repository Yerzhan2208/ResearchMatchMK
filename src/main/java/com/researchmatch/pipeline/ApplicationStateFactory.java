package com.researchmatch.pipeline;

import com.researchmatch.models.Application.Status;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * State factory: maps an {@link Status} to its {@link ApplicationState} bean (Member 3).
 *
 * <p>{@link #forStatus(Status)} is the only way other members obtain a state instance.
 */
@Component
public class ApplicationStateFactory {

    private final Map<Status, ApplicationState> states = new EnumMap<>(Status.class);

    /**
     * @param stateBeans every {@link ApplicationState} bean; one per status, otherwise the context fails fast
     */
    public ApplicationStateFactory(List<ApplicationState> stateBeans) {
        for (ApplicationState state : Objects.requireNonNull(stateBeans, "stateBeans")) {
            ApplicationState previous = states.put(state.status(), state);
            if (previous != null) {
                throw new IllegalStateException("Duplicate ApplicationState for " + state.status());
            }
        }
        for (Status status : Status.values()) {
            if (!states.containsKey(status)) {
                throw new IllegalStateException("Missing ApplicationState for " + status);
            }
        }
    }

    /**
     * @throws IllegalArgumentException if {@code status} is null
     */
    public ApplicationState forStatus(Status status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return states.get(status);
    }
}
