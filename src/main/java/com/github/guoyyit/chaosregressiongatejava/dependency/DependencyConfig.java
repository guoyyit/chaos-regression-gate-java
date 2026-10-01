package com.github.guoyyit.chaosregressiongatejava.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Wires the event notifier, applying the dependency fault plan when present.
 */
@Configuration
public class DependencyConfig {
    private static final Logger log = LoggerFactory.getLogger(DependencyConfig.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Bean
    public EventNotifier eventNotifier(@Value("${fault.plan:}") String faultPlanLocation) {
        LoggingEventNotifier delegate = new LoggingEventNotifier();
        if (faultPlanLocation == null || faultPlanLocation.isBlank()) {
            return delegate;
        }
        try {
            FaultPlan plan;
            File file = new File(faultPlanLocation);
            if (file.exists()) {
                plan = mapper.readValue(file, FaultPlan.class);
            } else {
                plan = mapper.readValue(faultPlanLocation, FaultPlan.class);
            }
            log.info("loaded fault plan for scenario '{}' with {} dependency faults",
                    plan.getScenario(), plan.getDependencyFaults().size());
            return new DependencyFaultInjector(delegate, plan);
        } catch (Exception e) {
            throw new IllegalStateException("failed to load fault plan from " + faultPlanLocation, e);
        }
    }
}
