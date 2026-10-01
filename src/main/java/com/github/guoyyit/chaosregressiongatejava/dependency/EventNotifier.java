package com.github.guoyyit.chaosregressiongatejava.dependency;

import java.util.List;

/**
 * Downstream dependency boundary: publishes resource change events.
 */
public interface EventNotifier {
    void publish(DependencyEvent event);
    List<DependencyEvent> observed();
}
