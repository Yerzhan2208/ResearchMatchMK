package com.researchmatch.pipeline;

import com.researchmatch.config.IllegalStateTransitionException;
import com.researchmatch.models.Application;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the deny-by-default transitions inherited from {@link AbstractApplicationState} for every state,
 * so all four base {@code deny(...)} paths and the base {@code describe()} are covered directly (independent
 * of the Spring wiring used by the pipeline tests).
 */
class AbstractApplicationStateCoverageTest {

    private final RejectedState rejected = new RejectedState();
    private final AcceptedState accepted = new AcceptedState();
    private final InterviewState interview =
            new InterviewState(accepted, rejected);
    private final ShortlistedState shortlisted =
            new ShortlistedState(interview, rejected);
    private final NewState newState =
            new NewState(shortlisted, rejected);

    @Test
    void newState_deniesInterviewAndAccept() {
        assertThatThrownBy(newState::interview).isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(newState::accept).isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void shortlisted_deniesShortlistAndAccept() {
        assertThatThrownBy(shortlisted::shortlist).isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(shortlisted::accept).isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void interview_deniesShortlistAndInterview() {
        assertThatThrownBy(interview::shortlist).isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(interview::interview).isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void terminalStates_denyEveryTransition() {
        for (ApplicationState terminal : new ApplicationState[]{accepted, rejected}) {
            assertThatThrownBy(terminal::shortlist).isInstanceOf(IllegalStateTransitionException.class);
            assertThatThrownBy(terminal::interview).isInstanceOf(IllegalStateTransitionException.class);
            assertThatThrownBy(terminal::accept).isInstanceOf(IllegalStateTransitionException.class);
            assertThatThrownBy(terminal::reject).isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Test
    void baseDescribe_isReachableViaADefaultSubclass() {
        // A subclass that does not override describe() uses AbstractApplicationState.describe().
        ApplicationState base = new ApplicationStateAdapter();
        assertThat(base.describe()).contains("Application is");
    }

    /** Minimal subclass that relies on every AbstractApplicationState default. */
    private static final class ApplicationStateAdapter extends AbstractApplicationState {
        @Override
        public Application.Status status() {
            return Application.Status.NEW;
        }
    }
}
