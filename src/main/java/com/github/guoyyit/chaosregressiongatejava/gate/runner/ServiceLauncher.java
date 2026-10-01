package com.github.guoyyit.chaosregressiongatejava.gate.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.chaosregressiongatejava.Application;
import com.github.guoyyit.chaosregressiongatejava.dependency.FaultPlan;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Launches an isolated service instance per scenario. Each instance gets an
 * independent port (server.port=0) and its own run directory; no mutable state
 * is shared between scenarios.
 */
public class ServiceLauncher {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public LaunchedService launch(File runDir, FaultPlan faultPlan) {
        try {
            File faultPlanFile = new File(runDir, "fault-plan.json");
            mapper.writeValue(faultPlanFile, faultPlan);

            Map<String, Object> props = new HashMap<>();
            props.put("server.port", 0);
            props.put("fault.plan", faultPlanFile.getAbsolutePath());
            props.put("spring.jmx.enabled", false);
            props.put("logging.level.root", "WARN");

            ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
                    .web(WebApplicationType.SERVLET)
                    .properties(props)
                    .run();

            int port = context.getEnvironment().getProperty("local.server.port", Integer.class, 0);
            waitUntilReady(port);
            return new LaunchedService(context, port);
        } catch (Exception e) {
            throw new IllegalStateException("failed to launch service for scenario in " + runDir, e);
        }
    }

    private void waitUntilReady(int port) {
        String url = "http://localhost:" + port + "/api/resources/r-0";
        for (int i = 0; i < 50; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() > 0) {
                    return;
                }
            } catch (Exception e) {
                // not ready yet
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("service on port " + port + " did not become ready");
    }

    /**
     * A launched service instance.
     */
    public static class LaunchedService {
        private final ConfigurableApplicationContext context;
        private final int port;

        public LaunchedService(ConfigurableApplicationContext context, int port) {
            this.context = context;
            this.port = port;
        }

        public int getPort() { return port; }
        public ConfigurableApplicationContext getContext() { return context; }

        public void close() {
            if (context != null) {
                context.close();
            }
        }
    }
}
