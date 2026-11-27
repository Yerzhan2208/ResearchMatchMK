package com.researchmatch.controllers;

import com.researchmatch.config.DuplicateEntityException;
import com.researchmatch.config.IllegalStateTransitionException;
import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = WebController.class)
public class MvcErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(MvcErrorAdvice.class);
    private static final String GENERIC_MESSAGE = "An unexpected error occurred.";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleNotFound(ResourceNotFoundException e) {
        return errorView(HttpStatus.NOT_FOUND, messageOrGeneric(e));
    }

    @ExceptionHandler({InvalidRequestException.class,
            IllegalStateTransitionException.class,
            DuplicateEntityException.class})
    public ModelAndView handleBadRequest(Exception e) {
        return errorView(HttpStatus.BAD_REQUEST, messageOrGeneric(e));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class})
    public ModelAndView handleBadInput(Exception e) {
        return errorView(HttpStatus.BAD_REQUEST, messageOrGeneric(e));
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception e) {
        log.error("Unhandled exception in web layer", e);
        return errorView(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_MESSAGE);
    }

    private static ModelAndView errorView(HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("message", message);
        mav.addObject("status", status.value());
        mav.setStatus(status);
        return mav;
    }

    private static String messageOrGeneric(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? GENERIC_MESSAGE : m;
    }
}
