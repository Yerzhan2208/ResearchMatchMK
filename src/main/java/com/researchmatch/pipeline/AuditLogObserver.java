package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import com.researchmatch.models.AuditEvent;
import com.researchmatch.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Observer that appends an {@code AuditEvent} for every transition.
 *
 * <p>Entities are {@code entityId = application id}, {@code action = "APPLICATION_STATUS_CHANGED"}.
 * Expected conditions (null application, unsaved application) are skipped rather than thrown: this runs
 * inside the subject's notify loop, which must never let one observer roll back a transition.
 */
@Component
public class AuditLogObserver implements ApplicationObserver {

    public static final String ACTION = "APPLICATION_STATUS_CHANGED";

    private static final Logger log = LoggerFactory.getLogger(AuditLogObserver.class);

    private final AuditEventRepository auditEventRepository;

    public AuditLogObserver(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public void onStatusChanged(Application application, Application.Status from, Application.Status to,
                                Instant at) {
        if (application == null || application.getId() == null) {
            log.warn("Skipping audit event for status {} -> {}: application is not persisted", from, to);
            return;
        }
        AuditEvent event = new AuditEvent(null, application.getId(), ACTION, details(from, to, at));
        auditEventRepository.save(event);
    }

    private static String details(Application.Status from, Application.Status to, Instant at) {
        String when = at == null ? "unknown time" : at.toString();
        return "Status changed from " + from + " to " + to + " at " + when;
    }
}
