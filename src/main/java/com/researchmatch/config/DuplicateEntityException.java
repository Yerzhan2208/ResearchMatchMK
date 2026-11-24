package com.researchmatch.config;

/** A persistence rule was violated (for example a duplicate student e-mail). */
public class DuplicateEntityException extends ResearchMatchException {

    private static final long serialVersionUID = 1L;

    public DuplicateEntityException(String message) {
        super(message);
    }
}
