package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import com.researchmatch.models.AuditEvent;
import com.researchmatch.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApplicationObserversTest {

    private static final Instant AT = Instant.parse("2026-01-02T03:04:05Z");

    @Mock
    private AuditEventRepository auditEventRepository;

    private Application application;

    @BeforeEach
    void setUp() {
        application = new Application(7L, 1L, 2L);
        application.setStatus(Application.Status.SHORTLISTED);
    }

    // ------------------------------------------------------------------ AuditLogObserver

    @Test
    void auditObserverPersistsOneEventPerTransition() {
        AuditLogObserver observer = new AuditLogObserver(auditEventRepository);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        observer.onStatusChanged(application, Application.Status.NEW, Application.Status.SHORTLISTED, AT);

        verify(auditEventRepository, times(1)).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getEntityId()).isEqualTo(7L);
        assertThat(event.getAction()).isEqualTo(AuditLogObserver.ACTION);
        assertThat(AuditLogObserver.ACTION).isEqualTo("APPLICATION_STATUS_CHANGED");
        assertThat(event.getDetails())
                .contains("NEW")
                .contains("SHORTLISTED")
                .contains(AT.toString());
        assertThat(event.getTimestamp()).isNull(); // stamped by @PrePersist in the entity
    }

    @Test
    void auditObserverHandlesAMissingInstant() {
        AuditLogObserver observer = new AuditLogObserver(auditEventRepository);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        observer.onStatusChanged(application, Application.Status.NEW, Application.Status.INTERVIEW, null);

        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).isNotBlank();
    }

    @Test
    void auditObserverSkipsRatherThanThrowsForUnusableInput() {
        AuditLogObserver observer = new AuditLogObserver(auditEventRepository);

        assertThatCode(() -> observer.onStatusChanged(
                null, Application.Status.NEW, Application.Status.SHORTLISTED, AT)).doesNotThrowAnyException();
        assertThatCode(() -> observer.onStatusChanged(
                new Application(null, 1L, 2L), Application.Status.NEW, Application.Status.SHORTLISTED, AT))
                .doesNotThrowAnyException();

        verifyNoInteractions(auditEventRepository);
    }

    // ------------------------------------------------------------------ NotificationObserver

    @Test
    void notificationObserverNeverThrows() {
        NotificationObserver observer = new NotificationObserver();

        assertThatCode(() -> observer.onStatusChanged(
                application, Application.Status.NEW, Application.Status.SHORTLISTED, AT))
                .doesNotThrowAnyException();
        assertThatCode(() -> observer.onStatusChanged(
                null, Application.Status.NEW, Application.Status.SHORTLISTED, AT))
                .doesNotThrowAnyException();
    }

    @Test
    void notificationObserverIsIdempotent() {
        NotificationObserver observer = new NotificationObserver();

        // Replaying the same transition must be harmless: logging has no side effects to accumulate.
        observer.onStatusChanged(application, Application.Status.NEW, Application.Status.SHORTLISTED, AT);
        observer.onStatusChanged(application, Application.Status.NEW, Application.Status.SHORTLISTED, AT);

        assertThat(application.getStatus()).isEqualTo(Application.Status.SHORTLISTED);
    }

    // ------------------------------------------------------------------ combined

    @Test
    void theSubjectDrivesBothDefaultObservers() {
        ApplicationSubject subject = new ApplicationSubject(
                List.of(new AuditLogObserver(auditEventRepository), new NotificationObserver()));

        subject.notifyStatusChanged(application, Application.Status.NEW, Application.Status.SHORTLISTED);

        verify(auditEventRepository, times(1)).save(any(AuditEvent.class));
    }
}
