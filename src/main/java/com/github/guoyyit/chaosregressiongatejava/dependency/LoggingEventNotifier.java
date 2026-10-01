package com.github.guoyyit.chaosregressiongatejava.dependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default in-memory event notifier. Records every published event with a
 * deterministic event id and delivery order.
 */
public class LoggingEventNotifier implements EventNotifier {
    private final List<DependencyEvent> observed = Collections.synchronizedList(new ArrayList<>());
    private long counter = 0;

    @Override
    public synchronized void publish(DependencyEvent event) {
        if (event.getEventId() == null) {
            event.setEventId("evt-" + (++counter));
        }
        observed.add(event);
    }

    @Override
    public List<DependencyEvent> observed() {
        synchronized (observed) {
            return new ArrayList<>(observed);
        }
    }
}
