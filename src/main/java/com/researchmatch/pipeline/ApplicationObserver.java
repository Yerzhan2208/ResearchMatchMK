package com.researchmatch.pipeline;

import com.researchmatch.models.Application;

import java.time.Instant;

/**
 * Observer notified after every successful application status change (Member 3).
 *
 * <p>Implementations must not throw for expected conditions; the subject logs and continues so a failing
 * observer cannot roll back a transition. Observers must be idempotent.
 */
public interface ApplicationObserver {

    /**
     * @param application never null, already carrying the new status
     * @param from        previous status, never null
     * @param to          new status, never null, different from {@code from}
     * @param at          when the transition happened, never null
     */
    void onStatusChanged(Application application, Application.Status from, Application.Status to, Instant at);
}
