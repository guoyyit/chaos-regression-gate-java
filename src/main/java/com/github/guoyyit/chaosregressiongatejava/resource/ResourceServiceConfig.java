package com.github.guoyyit.chaosregressiongatejava.resource;

import com.github.guoyyit.chaosregressiongatejava.dep.DependencyClient;
import com.github.guoyyit.chaosregressiongatejava.dep.DependencyFaultPlan;
import com.github.guoyyit.chaosregressiongatejava.dep.FaultInjectingDependencyClient;
import com.github.guoyyit.chaosregressiongatejava.dep.HttpDependencyClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResourceServiceConfig {

    @Bean
    public ResourceStore resourceStore() {
        return new ResourceStore();
    }

    @Bean
    public DependencyFaultPlan dependencyFaultPlan() {
        return new DependencyFaultPlan();
    }

    @Bean
    public DependencyClient dependencyClient(@Value("${dependency.base-url:}") String baseUrl,
                                             DependencyFaultPlan plan) {
        DependencyClient delegate = baseUrl == null || baseUrl.isBlank()
                ? new NoopDependencyClient()
                : new HttpDependencyClient(baseUrl);
        return new FaultInjectingDependencyClient(delegate, plan);
    }

    /** Used when no dependency is configured: audit is a no-op. */
    static class NoopDependencyClient implements DependencyClient {
        @Override
        public void audit(String resourceId, long version, String operation) {
        }
    }
}
