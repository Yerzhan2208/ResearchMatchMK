package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Observer that tells the student about the new application status.
 *
 * <p>Delivery channel is the application log (no mail gateway exists and adding a dependency is not
 * allowed). Logging is the cheapest idempotent channel: re-notifying the same transition writes the same
 * line and never mutates state, so the observer is safe to replay.
 */
@Component
public class NotificationObserver implements ApplicationObserver {

    private static final Logger log = LoggerFactory.getLogger(NotificationObserver.class);

    @Override
    public void onStatusChanged(Application application, Application.Status from, Application.Status to,
                                Instant at) {
        if (application == null) {
            log.warn("Skipping notification for status {} -> {}: application is null", from, to);
            return;
        }
        log.info("Notifying student {} about application {}: status changed from {} to {} at {}",
                application.getStudentId(), application.getId(), from, to, at);
    }
}
