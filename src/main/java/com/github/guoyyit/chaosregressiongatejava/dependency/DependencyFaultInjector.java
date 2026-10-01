package com.github.guoyyit.chaosregressiongatejava.dependency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Wraps an {@link EventNotifier} and applies dependency-boundary faults described
 * by a {@link FaultPlan}. Faults are keyed by the gate request id.
 */
public class DependencyFaultInjector implements EventNotifier {
    private static final Logger log = LoggerFactory.getLogger(DependencyFaultInjector.class);
    private static final long SAFETY_TIMEOUT_SECONDS = 30;

    private final EventNotifier delegate;
    private final Map<String, FaultPlan.DependencyFault> faultsByRequest = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    private final Map<String, Boolean> delivered = new ConcurrentHashMap<>();

    public DependencyFaultInjector(EventNotifier delegate, FaultPlan plan) {
        this.delegate = delegate;
        for (FaultPlan.DependencyFault f : plan.getDependencyFaults()) {
            faultsByRequest.put(f.getRequest(), f);
        }
    }

    @Override
    public void publish(DependencyEvent event) {
        String requestId = event.getRequestId();
        FaultPlan.DependencyFault fault = faultsByRequest.get(requestId);
        if (fault == null) {
            delegate.publish(event);
            markDelivered(requestId);
            return;
        }
        switch (fault.getType()) {
            case "LATENCY":
                sleep(fault.getDelayMs());
                delegate.publish(event);
                markDelivered(requestId);
                break;
            case "TIMEOUT":
                markDelivered(requestId);
                throw new DependencyTimeoutException("dependency timeout on request " + requestId);
            case "FAIL_MIDWAY":
                markDelivered(requestId);
                throw new DependencyFailureException("dependency failure midway on request " + requestId);
            case "DUPLICATE_DELIVERY":
                delegate.publish(event);
                delegate.publish(event);
                markDelivered(requestId);
                break;
            case "OUT_OF_ORDER":
                String after = fault.getAfterRequest();
                if (after != null) {
                    CountDownLatch latch = latches.computeIfAbsent(after, k -> new CountDownLatch(1));
                    try {
                        if (!latch.await(SAFETY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            log.warn("out-of-order wait for {} timed out", after);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                delegate.publish(event);
                markDelivered(requestId);
                break;
            default:
                delegate.publish(event);
                markDelivered(requestId);
        }
    }

    private void markDelivered(String requestId) {
        if (requestId != null) {
            delivered.put(requestId, Boolean.TRUE);
            latches.computeIfAbsent(requestId, k -> new CountDownLatch(1)).countDown();
        }
    }

    private void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<DependencyEvent> observed() {
        return delegate.observed();
    }

    /**
     * Thrown when the dependency times out.
     */
    public static class DependencyTimeoutException extends RuntimeException {
        public DependencyTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when the dependency fails midway.
     */
    public static class DependencyFailureException extends RuntimeException {
        public DependencyFailureException(String message) {
            super(message);
        }
    }
}
