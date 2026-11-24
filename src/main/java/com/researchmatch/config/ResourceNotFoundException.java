package com.researchmatch.config;

/** A requested entity, key, or subject could not be found. */
public class ResourceNotFoundException extends ResearchMatchException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
