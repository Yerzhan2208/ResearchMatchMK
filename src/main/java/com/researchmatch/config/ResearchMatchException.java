package com.researchmatch.config;

/**
 * Base type for every expected (recoverable) failure raised by the shared contracts.
 *
 * <p>Unchecked on purpose: callers at the web layer map these to 4xx responses, so they do not appear in
 * every method signature. Use the subclasses below rather than throwing this type directly.
 */
public class ResearchMatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResearchMatchException(String message) {
        super(message);
    }

    public ResearchMatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
