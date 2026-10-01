package com.github.guoyyit.chaosregressiongatejava.resource;

import com.github.guoyyit.chaosregressiongatejava.dependency.DependencyEvent;
import com.github.guoyyit.chaosregressiongatejava.dependency.DependencyFaultInjector;
import com.github.guoyyit.chaosregressiongatejava.dependency.EventNotifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stateful resource service with optimistic versioning and full version history.
 * Writes are rejected with {@link ResultCode#STALE_VERSION} when based on an
 * expired version. Every successful write publishes an event to the dependency.
 * <p>
 * The service is safe for concurrent access: the gate serializes same-resource
 * writes within a wave, and the underlying maps are concurrent. Methods are not
 * synchronized so that dependency-boundary faults (which may block) do not
 * deadlock against other requests.
 */
@Service
public class ResourceService {
    private final EventNotifier notifier;
    private final Map<String, ResourceRecord> store = new ConcurrentHashMap<>();
    private final Map<String, List<ResourceRecord>> history = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    public ResourceService(EventNotifier notifier) {
        this.notifier = notifier;
    }

    public ResourceResponse create(String idempotencyKey, Map<String, Object> data, String requestId, String resourceIdHint) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String existingId = idempotencyIndex.get(idempotencyKey);
            if (existingId != null) {
                ResourceRecord existing = store.get(existingId);
                return response(ResultCode.OK, existing, true);
            }
        }
        String id = resourceIdHint != null ? resourceIdHint : "r-" + idCounter.incrementAndGet();
        ResourceRecord record = new ResourceRecord();
        record.setId(id);
        record.setVersion(1);
        record.setData(data == null ? new HashMap<>() : new HashMap<>(data));
        store.put(id, record);
        history.computeIfAbsent(id, k -> new ArrayList<>()).add(copy(record));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyIndex.put(idempotencyKey, id);
        }
        ResourceResponse resp = response(ResultCode.CREATED, record, false);
        publish(id, 1, requestId, resp);
        return resp;
    }

    public ResourceResponse update(String id, long expectedVersion, Map<String, Object> data, String requestId) {
        ResourceRecord current = store.get(id);
        if (current == null) {
            return response(ResultCode.NOT_FOUND, null, false);
        }
        if (current.getVersion() != expectedVersion) {
            return response(ResultCode.STALE_VERSION, current, false);
        }
        long newVersion = current.getVersion() + 1;
        ResourceRecord updated = new ResourceRecord();
        updated.setId(id);
        updated.setVersion(newVersion);
        updated.setData(data == null ? new HashMap<>(current.getData()) : new HashMap<>(data));
        store.put(id, updated);
        history.computeIfAbsent(id, k -> new ArrayList<>()).add(copy(updated));
        ResourceResponse resp = response(ResultCode.OK, updated, false);
        publish(id, newVersion, requestId, resp);
        return resp;
    }

    public ResourceResponse read(String id) {
        ResourceRecord record = store.get(id);
        if (record == null) {
            return response(ResultCode.NOT_FOUND, null, false);
        }
        return response(ResultCode.OK, record, false);
    }

    public HistoryResponse history(String id) {
        List<ResourceRecord> versions = history.get(id);
        if (versions == null) {
            HistoryResponse resp = new HistoryResponse();
            resp.setCode(ResultCode.NOT_FOUND.name());
            resp.setId(id);
            return resp;
        }
        HistoryResponse resp = new HistoryResponse();
        resp.setCode(ResultCode.OK.name());
        resp.setId(id);
        resp.setVersions(new ArrayList<>(versions));
        return resp;
    }

    private void publish(String resourceId, long version, String requestId, ResourceResponse resp) {
        try {
            DependencyEvent event = new DependencyEvent();
            event.setResourceId(resourceId);
            event.setVersion(version);
            event.setRequestId(requestId);
            notifier.publish(event);
        } catch (DependencyFaultInjector.DependencyFailureException | DependencyFaultInjector.DependencyTimeoutException e) {
            // The write is already committed; only the dependency notification failed.
            resp.setCode(ResultCode.DEPENDENCY_FAILURE.name());
        }
    }

    private ResourceResponse response(ResultCode code, ResourceRecord record, boolean replayed) {
        ResourceResponse resp = new ResourceResponse();
        resp.setCode(code.name());
        if (record != null) {
            resp.setId(record.getId());
            resp.setVersion(record.getVersion());
            resp.setData(record.getData());
        }
        resp.setReplayed(replayed);
        return resp;
    }

    private ResourceRecord copy(ResourceRecord src) {
        ResourceRecord c = new ResourceRecord();
        c.setId(src.getId());
        c.setVersion(src.getVersion());
        c.setData(new HashMap<>(src.getData()));
        return c;
    }
}
