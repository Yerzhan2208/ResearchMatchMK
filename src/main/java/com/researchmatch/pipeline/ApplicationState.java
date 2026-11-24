package com.researchmatch.pipeline;

import com.researchmatch.config.IllegalStateTransitionException;
import com.researchmatch.models.Application.Status;

/**
 * State pattern: behaviour of an application in one particular {@link Status} (Member 3).
 *
 * <p>Allowed transitions are diagrammed in {@code docs/integration-contracts.md}. An unsupported
 * transition must raise {@link IllegalStateTransitionException}; states never persist anything themselves.
 */
public interface ApplicationState {

    /** The status this state represents. */
    Status status();

    /**
     * Move to SHORTLISTED.
     *
     * @throws IllegalStateTransitionException if not allowed from this state
     */
    ApplicationState shortlist();

    /**
     * Move to INTERVIEW.
     *
     * @throws IllegalStateTransitionException if not allowed from this state
     */
    ApplicationState interview();

    /**
     * Move to ACCEPTED.
     *
     * @throws IllegalStateTransitionException if not allowed from this state
     */
    ApplicationState accept();

    /**
     * Move to REJECTED.
     *
     * @throws IllegalStateTransitionException if not allowed from this state
     */
    ApplicationState reject();

    /** Human-readable note describing this state, used in UI and audit details. */
    String describe();
}
