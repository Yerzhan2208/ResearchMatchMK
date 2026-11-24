package com.researchmatch.pipeline;

import com.researchmatch.models.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer subject: holds the registered {@link ApplicationObserver}s and fans out transitions.
 *
 * <p>Register observers in the {@link #ApplicationSubject(List)} constructor injection so the default set
 * (audit log + notification) is always present, and use attach/detach for anything runtime-specific.
 * Thread-safe.
 */
@Component
@Scope("singleton")
public class ApplicationSubject {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSubject.class);

    private final CopyOnWriteArrayList<ApplicationObserver> observers = new CopyOnWriteArrayList<>();

    /**
     * @param initial zero or more observers; null entries are ignored
     */
    public ApplicationSubject(List<ApplicationObserver> initial) {
        if (initial != null) {
            initial.stream().filter(Objects::nonNull).forEach(observers::addIfAbsent);
        }
    }

    public void attach(ApplicationObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("observer must not be null");
        }
        observers.addIfAbsent(observer);
    }

    /**
     * @return true if the observer was registered
     */
    public boolean detach(ApplicationObserver observer) {
        return observers.remove(observer);
    }

    /** Snapshot of the current observers, in registration order. */
    public List<ApplicationObserver> observers() {
        return List.copyOf(observers);
    }

    /**
     * Notifies every observer exactly once; an observer that throws does not prevent the others from running
     * and never propagates to the caller, so a bad observer cannot roll back a transition.
     *
     * <p>One {@link Instant} is stamped per notification and shared by every observer, so all of them report
     * the same transition time.
     */
    public void notifyStatusChanged(Application application,
                                    Application.Status from,
                                    Application.Status to) {
        Objects.requireNonNull(application, "application must not be null");
        Objects.requireNonNull(from, "from status must not be null");
        Objects.requireNonNull(to, "to status must not be null");
        Instant at = Instant.now();
        // CopyOnWriteArrayList#iterator is an immutable snapshot: no concurrent-modification risk if an
        // observer attaches or detaches while we fan out.
        for (ApplicationObserver observer : observers) {
            try {
                observer.onStatusChanged(application, from, to, at);
            } catch (RuntimeException e) {
                log.warn("Observer {} failed for application {} ({} -> {}); continuing",
                        observer.getClass().getName(), application.getId(), from, to, e);
            }
        }
    }
}
