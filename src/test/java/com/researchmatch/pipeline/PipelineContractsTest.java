package com.researchmatch.pipeline;

import com.researchmatch.config.IllegalStateTransitionException;
import com.researchmatch.models.Application;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineContractsTest {

    private static final List<ApplicationState> ALL_STATES = List.of(
            new NewState(), new ShortlistedState(), new InterviewState(), new AcceptedState(), new RejectedState());

    @Test
    void everyStateMapsToExactlyOneStatus() {
        assertThat(ALL_STATES).extracting(ApplicationState::status)
                .containsExactly(Application.Status.NEW, Application.Status.SHORTLISTED,
                        Application.Status.INTERVIEW, Application.Status.ACCEPTED, Application.Status.REJECTED);
    }

    @Test
    void factoryResolvesEveryStatus() {
        ApplicationStateFactory factory = new ApplicationStateFactory(ALL_STATES);

        for (Application.Status status : Application.Status.values()) {
            assertThat(factory.forStatus(status).status()).isEqualTo(status);
        }
    }

    @Test
    void factoryFailsFastOnMissingOrDuplicateStates() {
        assertThatThrownBy(() -> new ApplicationStateFactory(List.of(new NewState())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing ApplicationState");
        assertThatThrownBy(() -> new ApplicationStateFactory(
                List.of(new NewState(), new NewState(), new ShortlistedState(), new InterviewState(),
                        new AcceptedState(), new RejectedState())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> new ApplicationStateFactory(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void factoryRejectsNullStatus() {
        ApplicationStateFactory factory = new ApplicationStateFactory(ALL_STATES);

        assertThatThrownBy(() -> factory.forStatus(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disallowedTransitionsRaiseIllegalStateTransition() {
        // An invalid move must never fall through silently; the message names the attempted transition.
        assertThatThrownBy(() -> new AcceptedState().shortlist())
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("shortlist");
        assertThatThrownBy(() -> new RejectedState().reject())
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    void placeholderTransitionsAreExplicitTodos() {
        // Allowed moves are Member 3's work; until then they must fail loudly, not return a wrong state.
        assertThatThrownBy(() -> new NewState().shortlist())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageStartingWith("TODO (Member 3)");
        assertThatThrownBy(() -> new ShortlistedState().interview())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ShortlistedState");
        assertThatThrownBy(() -> new InterviewState().accept())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("InterviewState");
    }

    @Nested
    class Subject {

        private final List<String> calls = new ArrayList<>();

        private ApplicationObserver recording(String tag) {
            return (application, from, to, at) -> calls.add(tag + ":" + from + "->" + to);
        }

        @Test
        void keepsRegistrationOrderAndIgnoresDuplicatesAndNulls() {
            ApplicationSubject subject = new ApplicationSubject(
                    Arrays.asList(recording("a"), null, recording("b")));
            ApplicationObserver first = subject.observers().get(0);

            subject.attach(first);
            subject.attach(recording("c"));

            assertThat(subject.observers()).hasSize(3);
        }

        @Test
        void attachRejectsNull() {
            ApplicationSubject subject = new ApplicationSubject(List.of());

            assertThatThrownBy(() -> subject.attach(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void detachReportsWhetherTheObserverWasRegistered() {
            ApplicationObserver observer = recording("a");
            ApplicationSubject subject = new ApplicationSubject(List.of(observer));

            assertThat(subject.detach(observer)).isTrue();
            assertThat(subject.detach(observer)).isFalse();
            assertThat(subject.observers()).isEmpty();
        }

        @Test
        void acceptsNullInitialList() {
            assertThat(new ApplicationSubject(null).observers()).isEmpty();
        }

        @Test
        void notifyIsStillATodo() {
            ApplicationSubject subject = new ApplicationSubject(List.of(recording("a")));
            Application application = new Application(1L, 1L, 1L);
            Instant before = Instant.now();

            assertThatThrownBy(() -> subject.notifyStatusChanged(
                    application, Application.Status.NEW, Application.Status.SHORTLISTED))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("TODO (Member 3)");
            assertThat(calls).isEmpty();
            assertThat(before).isNotNull();
        }
    }

    @Test
    void observersAreExplicitTodos() {
        Application application = new Application(1L, 1L, 1L);
        Instant now = Instant.now();

        assertThatThrownBy(() -> new AuditLogObserver().onStatusChanged(
                application, Application.Status.NEW, Application.Status.SHORTLISTED, now))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("AuditLogObserver");
        assertThatThrownBy(() -> new NotificationObserver().onStatusChanged(
                application, Application.Status.NEW, Application.Status.SHORTLISTED, now))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("NotificationObserver");
        assertThat(AuditLogObserver.ACTION).isEqualTo("APPLICATION_STATUS_CHANGED");
    }

    @Test
    void recruitmentPipelineEntriesAreExplicitTodos() {
        RecruitmentPipeline pipeline = new RecruitmentPipeline(
                org.mockito.Mockito.mock(com.researchmatch.repository.ApplicationRepository.class),
                org.mockito.Mockito.mock(com.researchmatch.repository.OpportunityRepository.class),
                new ApplicationStateFactory(ALL_STATES),
                new ApplicationSubject(List.of()));

        assertThatThrownBy(() -> pipeline.submit(1L, 1L, "hi"))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("submit");
        assertThatThrownBy(() -> pipeline.shortlist(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("shortlist");
        assertThatThrownBy(() -> pipeline.scheduleInterview(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("scheduleInterview");
        assertThatThrownBy(() -> pipeline.accept(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("accept");
        assertThatThrownBy(() -> pipeline.reject(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("reject");
        assertThatThrownBy(() -> pipeline.stateOf(1L))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("stateOf");
        assertThat(pipeline.getStateFactory()).isNotNull();
        assertThat(pipeline.getSubject()).isNotNull();
    }
}
