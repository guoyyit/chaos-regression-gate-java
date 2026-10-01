package com.github.guoyyit.chaosregressiongatejava.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.chaosregressiongatejava.dependency.DependencyEvent;
import com.github.guoyyit.chaosregressiongatejava.dependency.EventNotifier;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for stateful resources. Every operation returns the current version.
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final ResourceService service;
    private final EventNotifier notifier;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResourceController(ResourceService service, EventNotifier notifier) {
        this.service = service;
        this.notifier = notifier;
    }

    @PostMapping
    public ResourceResponse create(@RequestBody CreateResourceRequest body,
                                   @RequestHeader(value = "X-Gate-Request-Id", required = false) String requestId,
                                   @RequestHeader(value = "X-Gate-Resource-Id", required = false) String resourceIdHint,
                                   jakarta.servlet.http.HttpServletResponse response) {
        ResourceResponse resp = service.create(body.getIdempotencyKey(), body.getData(), requestId, resourceIdHint);
        attachDependencyObservations(response, requestId);
        return resp;
    }

    @PostMapping("/{id}/update")
    public ResourceResponse update(@PathVariable String id,
                                   @RequestBody UpdateResourceRequest body,
                                   @RequestHeader(value = "X-Gate-Request-Id", required = false) String requestId,
                                   jakarta.servlet.http.HttpServletResponse response) {
        ResourceResponse resp = service.update(id, body.getVersion(), body.getData(), requestId);
        attachDependencyObservations(response, requestId);
        return resp;
    }

    @GetMapping("/{id}")
    public ResourceResponse read(@PathVariable String id) {
        return service.read(id);
    }

    @GetMapping("/{id}/history")
    public HistoryResponse history(@PathVariable String id) {
        return service.history(id);
    }

    private void attachDependencyObservations(jakarta.servlet.http.HttpServletResponse response, String requestId) {
        if (requestId == null) {
            return;
        }
        try {
            List<DependencyEvent> all = notifier.observed();
            List<Map<String, Object>> observations = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                DependencyEvent e = all.get(i);
                if (requestId.equals(e.getRequestId())) {
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("eventId", e.getEventId());
                    o.put("resourceId", e.getResourceId());
                    o.put("version", e.getVersion());
                    o.put("order", i + 1);
                    observations.add(o);
                }
            }
            response.setHeader("X-Dependency-Observations", mapper.writeValueAsString(observations));
        } catch (Exception e) {
            // observations are best-effort
        }
    }
}
