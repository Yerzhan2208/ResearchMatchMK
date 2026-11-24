package com.researchmatch.config;

/** Caller supplied invalid input (blank required field, out-of-range value, ...). */
public class InvalidRequestException extends ResearchMatchException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(message);
    }
}
