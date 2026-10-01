package com.github.guoyyit.chaosregressiongatejava.gate.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.chaosregressiongatejava.gate.fault.FaultType;
import com.github.guoyyit.chaosregressiongatejava.gate.fault.InjectedFault;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.AttemptTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.DependencyEventTrace;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP client wrapper with client-boundary fault injection.
 * Faults are deterministic: given the same fault list, the same attempts are
 * produced. No ports or timestamps are recorded in the trace.
 */
public class GateHttpClient {
    private static final long TIMEOUT_OVERSHOOT_MS = 30;

    private final String baseUrl;
    private final long timeoutMs;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GateHttpClient(String baseUrl, long timeoutMs) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(timeoutMs, 1000)))
                .build();
    }

    /**
     * Result of sending a request: attempts and dependency observations.
     */
    public static class ClientResult {
        public List<AttemptTrace> attempts = new ArrayList<>();
        public List<DependencyEventTrace> dependencyEvents = new ArrayList<>();
    }

    public ClientResult send(String method, String path, Map<String, Object> body,
                             String requestId, List<InjectedFault> faults, String resourceIdHint) {
        ClientResult result = new ClientResult();
        boolean duplicate = false;
        for (InjectedFault f : faults) {
            switch (f.getType()) {
                case LATENCY:
                    sleep(f.getDelayMs());
                    break;
                case TIMEOUT:
                    sleep(timeoutMs + TIMEOUT_OVERSHOOT_MS);
                    result.attempts.add(attempt(1, "TIMEOUT", null, null, false));
                    return result;
                case CONNECTION_RESET:
                    result.attempts.add(attempt(1, "CONNECTION_FAILURE", null, null, false));
                    return result;
                case DUPLICATE_DELIVERY:
                    duplicate = true;
                    break;
                default:
                    break;
            }
        }
        result.attempts.add(doSend(method, path, body, requestId, 1, result, resourceIdHint));
        if (duplicate) {
            result.attempts.add(doSend(method, path, body, requestId, 2, result, resourceIdHint));
        }
        return result;
    }

    private AttemptTrace doSend(String method, String path, Map<String, Object> body,
                               String requestId, int attemptNo, ClientResult result, String resourceIdHint) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("X-Gate-Request-Id", requestId)
                    .header("Content-Type", "application/json");
            if (resourceIdHint != null) {
                builder.header("X-Gate-Resource-Id", resourceIdHint);
            }
            HttpRequest request;
            if ("GET".equalsIgnoreCase(method)) {
                request = builder.GET().build();
            } else {
                String json = body == null ? "{}" : mapper.writeValueAsString(body);
                request = builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();
            }
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            AttemptTrace attempt = parseAttempt(response.body(), attemptNo);
            parseDependencyEvents(response, result);
            return attempt;
        } catch (Exception e) {
            return attempt(attemptNo, "CONNECTION_FAILURE", null, null, false);
        }
    }

    private AttemptTrace parseAttempt(String body, int attemptNo) {
        try {
            JsonNode node = mapper.readTree(body);
            String code = node.has("code") ? node.get("code").asText() : "OK";
            String resourceId = node.has("id") && !node.get("id").isNull() ? node.get("id").asText() : null;
            Long version = node.has("version") && !node.get("version").isNull() ? node.get("version").asLong() : null;
            boolean replayed = node.has("replayed") && node.get("replayed").asBoolean();
            return attempt(attemptNo, code, resourceId, version, replayed);
        } catch (Exception e) {
            return attempt(attemptNo, "BAD_RESPONSE", null, null, false);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseDependencyEvents(HttpResponse<String> response, ClientResult result) {
        try {
            String header = response.headers().firstValue("X-Dependency-Observations").orElse(null);
            if (header == null || header.isBlank()) {
                return;
            }
            JsonNode arr = mapper.readTree(header);
            for (JsonNode n : arr) {
                DependencyEventTrace evt = new DependencyEventTrace();
                evt.setEventId(n.get("eventId").asText());
                evt.setResourceId(n.get("resourceId").asText());
                evt.setVersion(n.get("version").asLong());
                evt.setOrder(n.get("order").asInt());
                result.dependencyEvents.add(evt);
            }
        } catch (Exception e) {
            // best-effort
        }
    }

    private AttemptTrace attempt(int no, String code, String resourceId, Long version, boolean replayed) {
        AttemptTrace a = new AttemptTrace();
        a.setAttempt(no);
        a.setCode(code);
        a.setResourceId(resourceId);
        a.setVersion(version);
        a.setReplayed(replayed);
        return a;
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
}
