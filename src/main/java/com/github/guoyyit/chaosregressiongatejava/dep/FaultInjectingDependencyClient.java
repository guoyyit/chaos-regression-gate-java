package com.github.guoyyit.chaosregressiongatejava.dep;

/** Decorator that applies the configured fault plan at the dependency boundary. */
public class FaultInjectingDependencyClient implements DependencyClient {

    private final DependencyClient delegate;
    private final DependencyFaultPlan plan;

    public FaultInjectingDependencyClient(DependencyClient delegate, DependencyFaultPlan plan) {
        this.delegate = delegate;
        this.plan = plan;
    }

    @Override
    public void audit(String resourceId, long version, String operation) {
        String fault = plan.nextFault();
        if (fault == null) {
            delegate.audit(resourceId, version, operation);
            return;
        }
        switch (fault) {
            case "TIMEOUT" -> throw new DependencyException("injected dependency timeout");
            case "CONNECTION_RESET" -> throw new DependencyException("injected connection reset");
            case "MIDWAY_FAILURE" -> {
                // the dependency processed the call but failed before responding
                delegate.audit(resourceId, version, operation);
                throw new DependencyException("injected mid-processing failure");
            }
            case "DELAY" -> {
                // bounded real delay; the gate records the logical delay in the trace
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                delegate.audit(resourceId, version, operation);
            }
            default -> delegate.audit(resourceId, version, operation);
        }
    }
}
