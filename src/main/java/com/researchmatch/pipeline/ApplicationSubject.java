package com.researchmatch.pipeline;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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
     * Notifies every observer; an observer that throws does not prevent the others from running.
     *
     * <p>TODO (Member 3): log failures.
     */
    public void notifyStatusChanged(com.researchmatch.models.Application application,
                                    com.researchmatch.models.Application.Status from,
                                    com.researchmatch.models.Application.Status to) {
        throw new UnsupportedOperationException("TODO (Member 3): ApplicationSubject.notifyStatusChanged");
    }
}
