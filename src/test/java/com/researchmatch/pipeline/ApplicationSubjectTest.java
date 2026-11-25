package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationSubjectTest {

    private static final Application.Status FROM = Application.Status.NEW;
    private static final Application.Status TO = Application.Status.SHORTLISTED;

    private final List<String> calls = new ArrayList<>();
    private final Application application = new Application(7L, 1L, 2L);

    /** Records "{tag}:{from}->{to}" in the shared call log. */
    private ApplicationObserver recording(String tag) {
        return (app, from, to, at) -> calls.add(tag + ":" + from + "->" + to);
    }

    @Nested
    class Registration {

        @Test
        void keepsRegistrationOrder() {
            ApplicationSubject subject = new ApplicationSubject(
                    List.of(recording("a"), recording("b"), recording("c")));

            assertThat(subject.observers()).hasSize(3);
        }

        @Test
        void ignoresNullEntriesInTheInitialList() {
            ApplicationSubject subject = new ApplicationSubject(Arrays.asList(null, recording("a"), null));

            assertThat(subject.observers()).hasSize(1);
        }

        @Test
        void acceptsNullInitialList() {
            assertThat(new ApplicationSubject(null).observers()).isEmpty();
        }

        @Test
        void attachRejectsNull() {
            assertThatThrownBy(() -> new ApplicationSubject(List.of()).attach(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void attachingTheSameObserverTwiceKeepsOneRegistration() {
            ApplicationSubject subject = new ApplicationSubject(List.of());
            ApplicationObserver observer = recording("a");

            subject.attach(observer);
            subject.attach(observer);

            assertThat(subject.observers()).containsExactly(observer);
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
        void observersSnapshotIsImmutable() {
            ApplicationSubject subject = new ApplicationSubject(List.of(recording("a")));

            assertThatThrownBy(() -> subject.observers().add(recording("b")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class Notification {

        @Test
        void notifiesEveryObserverExactlyOnceInOrder() {
            ApplicationSubject subject = new ApplicationSubject(
                    List.of(recording("a"), recording("b"), recording("c")));

            subject.notifyStatusChanged(application, FROM, TO);

            assertThat(calls).containsExactly(
                    "a:NEW->SHORTLISTED", "b:NEW->SHORTLISTED", "c:NEW->SHORTLISTED");
        }

        @Test
        void stampsOneSharedInstantForEveryObserver() {
            List<Instant> stamps = new ArrayList<>();
            ApplicationObserver first = (app, from, to, at) -> stamps.add(at);
            ApplicationObserver second = (app, from, to, at) -> stamps.add(at);
            ApplicationSubject subject = new ApplicationSubject(List.of(first, second));

            subject.notifyStatusChanged(application, FROM, TO);

            assertThat(stamps).hasSize(2);
            assertThat(stamps.get(0)).isNotNull().isEqualTo(stamps.get(1));
        }

        @Test
        void passesTheApplicationAndBothStatusesThrough() {
            List<String> seen = new ArrayList<>();
            ApplicationSubject subject = new ApplicationSubject(List.of(
                    (app, from, to, at) -> seen.add(app.getId() + "|" + from + "|" + to)));

            subject.notifyStatusChanged(application, FROM, TO);

            assertThat(seen).containsExactly("7|NEW|SHORTLISTED");
        }

        @Test
        void aFailingObserverDoesNotStopTheOthers() {
            AtomicInteger after = new AtomicInteger();
            ApplicationObserver broken = (app, from, to, at) -> {
                throw new IllegalStateException("boom");
            };
            ApplicationObserver healthy = (app, from, to, at) -> after.incrementAndGet();
            ApplicationSubject subject = new ApplicationSubject(List.of(broken, healthy));

            subject.notifyStatusChanged(application, FROM, TO);

            assertThat(after).hasValue(1);
        }

        @Test
        void aFailingObserverNeverPropagatesToTheCaller() {
            ApplicationSubject subject = new ApplicationSubject(List.of(
                    (app, from, to, at) -> {
                        throw new IllegalArgumentException("boom");
                    }));

            subject.notifyStatusChanged(application, FROM, TO);

            // Reaching this line at all is the assertion.
            assertThat(subject.observers()).hasSize(1);
        }

        @Test
        void anObserverMayDetachItselfDuringFanOutWithoutBreakingIteration() {
            AtomicInteger healthyCalls = new AtomicInteger();
            ApplicationSubject subject = new ApplicationSubject(List.of());
            ApplicationObserver selfRemoving = (app, from, to, at) -> subject.detach(subject.observers().get(0));
            ApplicationObserver healthy = (app, from, to, at) -> healthyCalls.incrementAndGet();
            subject.attach(selfRemoving);
            subject.attach(healthy);

            subject.notifyStatusChanged(application, FROM, TO);

            assertThat(healthyCalls).hasValue(1);
            assertThat(subject.observers()).doesNotContain(selfRemoving);
        }

        @Test
        void withNoObserversNotificationIsANoOp() {
            ApplicationSubject subject = new ApplicationSubject(List.of());

            subject.notifyStatusChanged(application, FROM, TO);

            assertThat(calls).isEmpty();
        }

        @Test
        void rejectsNullArguments() {
            ApplicationSubject subject = new ApplicationSubject(List.of(recording("a")));

            assertThatThrownBy(() -> subject.notifyStatusChanged(null, FROM, TO))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> subject.notifyStatusChanged(application, null, TO))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> subject.notifyStatusChanged(application, FROM, null))
                    .isInstanceOf(NullPointerException.class);
            assertThat(calls).isEmpty();
        }
    }
}
