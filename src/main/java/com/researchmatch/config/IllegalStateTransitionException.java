package com.researchmatch.config;

/** A recruitment transition that the current {@code ApplicationState} does not allow. */
public class IllegalStateTransitionException extends ResearchMatchException {

    private static final long serialVersionUID = 1L;

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
