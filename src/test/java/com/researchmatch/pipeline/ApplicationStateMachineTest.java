package com.researchmatch.pipeline;

import com.researchmatch.config.IllegalStateTransitionException;
import com.researchmatch.models.Application;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exhaustive coverage of the transition matrix in {@code docs/integration-contracts.md} §5.
 *
 * <p>All five states are real, wired-instance objects (the same graph Spring builds).
 */
class ApplicationStateMachineTest {

    private AcceptedState accepted;
    private RejectedState rejected;
    private InterviewState interview;
    private ShortlistedState shortlisted;
    private NewState fresh;

    @BeforeEach
    void wireTheStateGraph() {
        accepted = new AcceptedState();
        rejected = new RejectedState();
        interview = new InterviewState(accepted, rejected);
        shortlisted = new ShortlistedState(interview, rejected);
        fresh = new NewState(shortlisted, rejected);
    }

    private ApplicationState stateFor(Application.Status status) {
        return new ApplicationStateFactory(List.of(fresh, shortlisted, interview, accepted, rejected))
                .forStatus(status);
    }

    private static IllegalStateTransitionException denied(Supplier<ApplicationState> move) {
        try {
            move.get();
        } catch (IllegalStateTransitionException e) {
            return e;
        }
        throw new AssertionError("Expected the transition to be rejected");
    }

    /** Applies the transition named by {@code target} to {@code state}, for matrix-style assertions. */
    private static ApplicationState move(ApplicationState state, Application.Status target) {
        return switch (target) {
            case SHORTLISTED -> state.shortlist();
            case INTERVIEW -> state.interview();
            case ACCEPTED -> state.accept();
            case REJECTED -> state.reject();
            case NEW -> throw new IllegalArgumentException("NEW is never a transition target");
        };
    }

    // ------------------------------------------------------------------ valid transitions

    @Nested
    @DisplayName("allowed transitions land on the expected state")
    class Allowed {

        @Test
        void newShortlistsToShortlisted() {
            assertThat(fresh.shortlist().status()).isEqualTo(Application.Status.SHORTLISTED);
        }

        @Test
        void newRejectsToRejected() {
            assertThat(fresh.reject().status()).isEqualTo(Application.Status.REJECTED);
        }

        @Test
        void shortlistedInterviewsToInterview() {
            assertThat(shortlisted.interview().status()).isEqualTo(Application.Status.INTERVIEW);
        }

        @Test
        void shortlistedRejectsToRejected() {
            assertThat(shortlisted.reject().status()).isEqualTo(Application.Status.REJECTED);
        }

        @Test
        void interviewAcceptsToAccepted() {
            assertThat(interview.accept().status()).isEqualTo(Application.Status.ACCEPTED);
        }

        @Test
        void interviewRejectsToRejected() {
            assertThat(interview.reject().status()).isEqualTo(Application.Status.REJECTED);
        }

        @Test
        void theHappyPathWalksEndToEnd() {
            ApplicationState state = fresh;
            state = stateFor(state.shortlist().status());
            state = stateFor(state.interview().status());
            state = stateFor(state.accept().status());

            assertThat(state.status()).isEqualTo(Application.Status.ACCEPTED);
        }

        @Test
        void allowedMovesHandBackTheSharedSingletonBeans() {
            assertThat(fresh.shortlist()).isSameAs(shortlisted);
            assertThat(fresh.reject()).isSameAs(rejected);
            assertThat(shortlisted.interview()).isSameAs(interview);
            assertThat(shortlisted.reject()).isSameAs(rejected);
            assertThat(interview.accept()).isSameAs(accepted);
            assertThat(interview.reject()).isSameAs(rejected);
        }
    }

    // ------------------------------------------------------------------ invalid transitions

    @Nested
    @DisplayName("disallowed transitions raise IllegalStateTransitionException")
    class Denied {

        @Test
        void newCannotSkipStraightToInterview() {
            assertThat(denied(fresh::interview).getMessage()).contains("interview").contains("NEW");
        }

        @Test
        void newCannotBeAcceptedDirectly() {
            IllegalStateTransitionException error = denied(fresh::accept);
            assertThat(error.getMessage()).contains("accept").contains("NEW");
        }

        @Test
        void shortlistedCannotBeShortlistedAgain() {
            assertThat(denied(shortlisted::shortlist).getMessage())
                    .contains("shortlist").contains("SHORTLISTED");
        }

        @Test
        void shortlistedCannotBeAcceptedWithoutAnInterview() {
            assertThat(denied(shortlisted::accept).getMessage()).contains("accept").contains("SHORTLISTED");
        }

        @Test
        void interviewCannotBeShortlistedAgain() {
            assertThat(denied(interview::shortlist).getMessage()).contains("INTERVIEW");
        }

        @Test
        void interviewCannotSkipToInterviewAgain() {
            assertThat(denied(interview::interview).getMessage()).contains("INTERVIEW");
        }

        @ParameterizedTest
        @EnumSource(value = Application.Status.class, names = {"SHORTLISTED", "INTERVIEW", "ACCEPTED", "REJECTED"})
        void acceptedIsTerminal(Application.Status target) {
            ApplicationState acceptedState = stateFor(Application.Status.ACCEPTED);

            assertThatThrownBy(() -> move(acceptedState, target))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @ParameterizedTest
        @EnumSource(value = Application.Status.class, names = {"SHORTLISTED", "INTERVIEW", "ACCEPTED", "REJECTED"})
        void rejectedIsTerminal(Application.Status target) {
            ApplicationState rejectedState = stateFor(Application.Status.REJECTED);

            assertThatThrownBy(() -> move(rejectedState, target))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        void acceptedRejectsEveryMoveAndNamesItself() {
            ApplicationState acceptedState = stateFor(Application.Status.ACCEPTED);

            assertThat(denied(acceptedState::shortlist).getMessage()).contains("ACCEPTED");
            assertThat(denied(acceptedState::interview).getMessage()).contains("ACCEPTED");
            assertThat(denied(acceptedState::accept).getMessage()).contains("ACCEPTED");
            assertThat(denied(acceptedState::reject).getMessage()).contains("ACCEPTED");
        }

        @Test
        void rejectedIsImmutable() {
            ApplicationState rejectedState = stateFor(Application.Status.REJECTED);

            assertThat(denied(rejectedState::shortlist).getMessage()).contains("REJECTED");
            assertThat(denied(rejectedState::interview).getMessage()).contains("REJECTED");
            assertThat(denied(rejectedState::accept).getMessage()).contains("REJECTED");
            assertThat(denied(rejectedState::reject).getMessage()).contains("REJECTED");
        }
    }

    // ------------------------------------------------------------------ descriptions

    @Nested
    @DisplayName("describe()")
    class Descriptions {

        @Test
        void everyStateDescribesItselfUniquelyAndNonBlank() {
            List<String> descriptions = List.of(fresh, shortlisted, interview, accepted, rejected).stream()
                    .map(ApplicationState::describe)
                    .toList();

            assertThat(descriptions).allSatisfy(d -> assertThat(d).isNotBlank())
                    .doesNotHaveDuplicates();
            assertThat(descriptions.get(0)).contains("new");
            assertThat(descriptions.get(4)).contains("rejected");
        }

        @Test
        void terminalStatesSayTheyAreFinal() {
            assertThat(accepted.describe()).contains("final");
            assertThat(rejected.describe()).contains("final");
        }
    }

    // ------------------------------------------------------------------ wiring

    @Nested
    @DisplayName("factory wiring")
    class Wiring {

        @Test
        void everyStatusResolvesToItsOwnState() {
            ApplicationStateFactory factory =
                    new ApplicationStateFactory(List.of(fresh, shortlisted, interview, accepted, rejected));

            assertThat(factory.forStatus(Application.Status.NEW)).isSameAs(fresh);
            assertThat(factory.forStatus(Application.Status.SHORTLISTED)).isSameAs(shortlisted);
            assertThat(factory.forStatus(Application.Status.INTERVIEW)).isSameAs(interview);
            assertThat(factory.forStatus(Application.Status.ACCEPTED)).isSameAs(accepted);
            assertThat(factory.forStatus(Application.Status.REJECTED)).isSameAs(rejected);
        }

        @Test
        void nullStatusIsRejected() {
            ApplicationStateFactory factory =
                    new ApplicationStateFactory(List.of(fresh, shortlisted, interview, accepted, rejected));

            assertThatThrownBy(() -> factory.forStatus(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
