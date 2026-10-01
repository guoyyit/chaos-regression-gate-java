package com.github.guoyyit.chaosregressiongatejava.dep;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Plan describing which dependency calls should fail and how.
 *  Call indices are 1-based and counted per plan installation, which keeps
 *  behaviour deterministic for a sequential scenario run. */
public class DependencyFaultPlan {

    private final Map<Long, String> faultsByCallIndex = new ConcurrentHashMap<>();
    private final AtomicLong callCounter = new AtomicLong();

    public synchronized void setFaults(Map<Long, String> faults) {
        faultsByCallIndex.clear();
        faultsByCallIndex.putAll(faults);
        callCounter.set(0);
    }

    /** Returns the fault type name for the next call, or null for a clean call. */
    public String nextFault() {
        long index = callCounter.incrementAndGet();
        return faultsByCallIndex.get(index);
    }

    public synchronized void reset() {
        faultsByCallIndex.clear();
        callCounter.set(0);
    }
}
