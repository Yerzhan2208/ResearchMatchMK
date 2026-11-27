package com.researchmatch.controllers;

import com.researchmatch.config.DuplicateEntityException;
import com.researchmatch.config.IllegalStateTransitionException;
import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

class MvcErrorAdviceTest {

    private final MvcErrorAdvice advice = new MvcErrorAdvice();

    @Test
    void notFoundRenders404ErrorView() {
        ModelAndView mav = advice.handleNotFound(new ResourceNotFoundException("missing"));

        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getStatus().value()).isEqualTo(404);
        assertThat(mav.getModel().get("message")).isEqualTo("missing");
        assertThat(mav.getModel().get("status")).isEqualTo(404);
    }

    @Test
    void badRequestRenders400ForEachSubtype() {
        Exception[] exceptions = {
                new InvalidRequestException("bad"),
                new IllegalStateTransitionException("no"),
                new DuplicateEntityException("dup")
        };

        for (Exception e : exceptions) {
            ModelAndView mav = advice.handleBadRequest(e);

            assertThat(mav.getViewName()).isEqualTo("error");
            assertThat(mav.getStatus().value()).isEqualTo(400);
            assertThat(mav.getModel().get("message")).isEqualTo(e.getMessage());
            assertThat(mav.getModel().get("status")).isEqualTo(400);
        }
    }

    @Test
    void blankMessageFallsBackToGeneric() {
        ModelAndView mav = advice.handleBadRequest(new InvalidRequestException("  "));

        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getStatus().value()).isEqualTo(400);
        assertThat(mav.getModel().get("message")).isEqualTo("An unexpected error occurred.");
    }

    @Test
    void nullMessageFallsBackToGeneric() {
        ModelAndView mav = advice.handleNotFound(new ResourceNotFoundException(null));

        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getStatus().value()).isEqualTo(404);
        assertThat(mav.getModel().get("message")).isEqualTo("An unexpected error occurred.");
    }

    @Test
    void badInputRenders400() {
        ModelAndView mav = advice.handleBadInput(
                new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                        "abc", Long.class, "id", null, null));

        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getStatus().value()).isEqualTo(400);
        assertThat(mav.getModel().get("status")).isEqualTo(400);
    }

    @Test
    void unexpectedExceptionRendersGeneric500() {
        ModelAndView mav = advice.handleUnexpected(new RuntimeException("secret-password-detail"));

        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getStatus().value()).isEqualTo(500);
        assertThat(mav.getModel().get("message")).isEqualTo("An unexpected error occurred.");
        assertThat((String) mav.getModel().get("message")).doesNotContain("secret-password-detail");
        assertThat(mav.getModel().get("status")).isEqualTo(500);
    }
}
