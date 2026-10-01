package com.github.guoyyit.chaosregressiongatejava.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory versioned resource store with full history per resource. */
public class ResourceStore {

    private final Map<String, List<ResourceRecord>> history = new ConcurrentHashMap<>();

    public synchronized ResourceRecord create(String id, String data) {
        List<ResourceRecord> versions = history.get(id);
        if (versions != null && !versions.isEmpty()) {
            throw new ApiException(ResultCode.ALREADY_EXISTS, "resource already exists: " + id);
        }
        ResourceRecord created = new ResourceRecord(id, 1L, data);
        List<ResourceRecord> list = new ArrayList<>();
        list.add(created);
        history.put(id, list);
        return created;
    }

    public synchronized ResourceRecord update(String id, long expectedVersion, String data) {
        List<ResourceRecord> versions = history.get(id);
        if (versions == null || versions.isEmpty()) {
            throw new ApiException(ResultCode.NOT_FOUND, "resource not found: " + id);
        }
        ResourceRecord current = versions.get(versions.size() - 1);
        if (current.version() != expectedVersion) {
            throw new ApiException(ResultCode.VERSION_CONFLICT,
                    "stale version " + expectedVersion + ", current is " + current.version());
        }
        ResourceRecord updated = new ResourceRecord(id, current.version() + 1, data);
        versions.add(updated);
        return updated;
    }

    public ResourceRecord get(String id) {
        return latest(id);
    }

    public List<ResourceRecord> history(String id) {
        List<ResourceRecord> versions = history.get(id);
        if (versions == null || versions.isEmpty()) {
            throw new ApiException(ResultCode.NOT_FOUND, "resource not found: " + id);
        }
        synchronized (this) {
            return List.copyOf(versions);
        }
    }

    public synchronized void reset() {
        history.clear();
    }

    private ResourceRecord latest(String id) {
        List<ResourceRecord> versions = history.get(id);
        if (versions == null || versions.isEmpty()) {
            throw new ApiException(ResultCode.NOT_FOUND, "resource not found: " + id);
        }
        return versions.get(versions.size() - 1);
    }
}
