package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Observer that tells the student about the new application status.
 *
 * <p>TODO (Member 3): decide the delivery channel (log/in-app/e-mail) and implement it; never throw to the
 * caller. Injected here: a placeholder for the mail/notification gateway.
 */
@Component
public class NotificationObserver implements ApplicationObserver {

    @Override
    public void onStatusChanged(Application application, Application.Status from, Application.Status to,
                                Instant at) {
        throw new UnsupportedOperationException("TODO (Member 3): NotificationObserver.onStatusChanged");
    }
}
