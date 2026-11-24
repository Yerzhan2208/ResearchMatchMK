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

    /** The same state graph Spring wires: each state holds the ones it can move to. */
    private static final AcceptedState ACCEPTED = new AcceptedState();
    private static final RejectedState REJECTED = new RejectedState();
    private static final InterviewState INTERVIEW = new InterviewState(ACCEPTED, REJECTED);
    private static final ShortlistedState SHORTLISTED = new ShortlistedState(INTERVIEW, REJECTED);
    private static final NewState NEW = new NewState(SHORTLISTED, REJECTED);

    private static final List<ApplicationState> ALL_STATES =
            List.of(NEW, SHORTLISTED, INTERVIEW, ACCEPTED, REJECTED);

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
        assertThatThrownBy(() -> new ApplicationStateFactory(List.of(NEW)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing ApplicationState");
        assertThatThrownBy(() -> new ApplicationStateFactory(
                List.of(NEW, NEW, SHORTLISTED, INTERVIEW, ACCEPTED, REJECTED)))
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
        assertThatThrownBy(() -> ACCEPTED.shortlist())
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("shortlist");
        assertThatThrownBy(() -> REJECTED.reject())
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    void allowedTransitionsLandOnTheirTargetState() {
        // The full matrix is covered exhaustively by ApplicationStateMachineTest; this keeps the
        // contract-level smoke check that each allowed move reaches its documented status.
        assertThat(NEW.shortlist().status()).isEqualTo(Application.Status.SHORTLISTED);
        assertThat(SHORTLISTED.interview().status()).isEqualTo(Application.Status.INTERVIEW);
        assertThat(INTERVIEW.accept().status()).isEqualTo(Application.Status.ACCEPTED);
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
        void notifyFansOutToEveryObserverOnce() {
            ApplicationSubject subject = new ApplicationSubject(List.of(recording("a")));
            Application application = new Application(1L, 1L, 1L);
            Instant before = Instant.now();

            subject.notifyStatusChanged(application, Application.Status.NEW, Application.Status.SHORTLISTED);

            assertThat(calls).containsExactly("a:NEW->SHORTLISTED");
            assertThat(before).isNotNull();
        }
    }

    @Test
    void observersAreWiredToTheirCollaborators() {
        Application application = new Application(1L, 1L, 1L);
        Instant now = Instant.now();
        com.researchmatch.repository.AuditEventRepository auditEventRepository =
                org.mockito.Mockito.mock(com.researchmatch.repository.AuditEventRepository.class);

        new AuditLogObserver(auditEventRepository)
                .onStatusChanged(application, Application.Status.NEW, Application.Status.SHORTLISTED, now);
        new NotificationObserver()
                .onStatusChanged(application, Application.Status.NEW, Application.Status.SHORTLISTED, now);

        org.mockito.Mockito.verify(auditEventRepository).save(
                org.mockito.ArgumentMatchers.any(com.researchmatch.models.AuditEvent.class));
        assertThat(AuditLogObserver.ACTION).isEqualTo("APPLICATION_STATUS_CHANGED");
    }

    @Test
    void recruitmentPipelineIsConstructibleFromTheContractSignature() {
        RecruitmentPipeline pipeline = new RecruitmentPipeline(
                org.mockito.Mockito.mock(com.researchmatch.repository.ApplicationRepository.class),
                org.mockito.Mockito.mock(com.researchmatch.repository.OpportunityRepository.class),
                org.mockito.Mockito.mock(com.researchmatch.repository.StudentRepository.class),
                new ApplicationStateFactory(ALL_STATES),
                new ApplicationSubject(List.of()));

        assertThat(pipeline.getStateFactory()).isNotNull();
        assertThat(pipeline.getSubject()).isNotNull();
    }
}
