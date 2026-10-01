package com.github.guoyyit.chaosregressiongatejava.resource;

import com.github.guoyyit.chaosregressiongatejava.dep.DependencyClient;
import com.github.guoyyit.chaosregressiongatejava.dep.DependencyException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for stateful, versioned resources. */
@RestController
@RequestMapping("/resources")
public class ResourceController {

    private final ResourceStore store;
    private final DependencyClient dependencyClient;

    public ResourceController(ResourceStore store, DependencyClient dependencyClient) {
        this.store = store;
        this.dependencyClient = dependencyClient;
    }

    public record CreateRequest(String data) {
    }

    public record UpdateRequest(Long expectedVersion, String data) {
    }

    @PostMapping("/{id}")
    public ResponseEntity<ApiResponse> create(@PathVariable String id, @RequestBody CreateRequest request) {
        ResourceRecord created = store.create(id, request.data());
        audit(id, created.version(), "CREATE");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(ResultCode.CREATED, created));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable String id, @RequestBody UpdateRequest request) {
        if (request.expectedVersion() == null) {
            throw new ApiException(ResultCode.BAD_REQUEST, "expectedVersion is required");
        }
        ResourceRecord updated = store.update(id, request.expectedVersion(), request.data());
        audit(id, updated.version(), "UPDATE");
        return ApiResponse.of(ResultCode.OK, updated);
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable String id) {
        return ApiResponse.of(ResultCode.OK, store.get(id));
    }

    @GetMapping("/{id}/history")
    public List<ResourceRecord> history(@PathVariable String id) {
        return store.history(id);
    }

    private void audit(String id, long version, String operation) {
        try {
            dependencyClient.audit(id, version, operation);
        } catch (DependencyException e) {
            // mid-processing failure: the mutation is already committed
            throw new ApiException(ResultCode.DEPENDENCY_FAILURE, e.getMessage());
        }
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse> handleApi(ApiException e) {
        HttpStatus status = switch (e.code()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VERSION_CONFLICT -> HttpStatus.CONFLICT;
            case ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case DEPENDENCY_FAILURE -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(ApiResponse.error(e.code(), e.getMessage()));
    }
}
