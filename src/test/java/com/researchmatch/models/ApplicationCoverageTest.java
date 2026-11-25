package com.researchmatch.models;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@link Application#setUpdatedAt} setter and identity equality self-branch, which the existing
 * model tests do not touch. Kept separate from the existing model test classes.
 */
class ApplicationCoverageTest {

    @Test
    void setUpdatedAtRoundTrips() {
        Application application = new Application();
        LocalDateTime when = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        application.setUpdatedAt(when);
        assertThat(application.getUpdatedAt()).isEqualTo(when);
    }

    @Test
    void equalitySelfAndTypeBranches() {
        Application application = new Application(1L, 2L, 3L);
        assertThat(application).isEqualTo(application);
        assertThat(application).isNotEqualTo("not an application");
    }
}
