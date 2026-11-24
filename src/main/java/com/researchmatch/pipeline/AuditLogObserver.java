package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Observer that appends an {@code AuditEvent} for every transition.
 *
 * <p>TODO (Member 3): inject {@code AuditEventRepository} (Member 1) and persist
 * (entityId = application id, action = "APPLICATION_STATUS_CHANGED").
 */
@Component
public class AuditLogObserver implements ApplicationObserver {

    public static final String ACTION = "APPLICATION_STATUS_CHANGED";

    @Override
    public void onStatusChanged(Application application, Application.Status from, Application.Status to,
                                Instant at) {
        throw new UnsupportedOperationException("TODO (Member 3): AuditLogObserver.onStatusChanged");
    }
}
