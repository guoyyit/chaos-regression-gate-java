package com.github.guoyyit.chaosregressiongatejava.tests.gate;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Contract of the stateful resource API: versions on every mutation,
 *  stale writes rejected with a stable code, history traceable. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResourceApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void versionedCrudWithHistory() {
        ResponseEntity<Map> created = rest.postForEntity("/resources/res-1",
                Map.of("data", "alpha"), Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("CREATED", created.getBody().get("code"));
        assertEquals(1, ((Number) created.getBody().get("version")).intValue());

        ResponseEntity<Map> updated = rest.exchange("/resources/res-1", HttpMethod.PUT,
                new HttpEntity<>(Map.of("expectedVersion", 1, "data", "beta")), Map.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals(2, ((Number) updated.getBody().get("version")).intValue());

        ResponseEntity<Map> stale = rest.exchange("/resources/res-1", HttpMethod.PUT,
                new HttpEntity<>(Map.of("expectedVersion", 1, "data", "gamma")), Map.class);
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals("VERSION_CONFLICT", stale.getBody().get("code"),
                "stale writes must be rejected with a stable result code");

        ResponseEntity<Map> current = rest.getForEntity("/resources/res-1", Map.class);
        assertEquals("beta", current.getBody().get("data"));
        assertEquals(2, ((Number) current.getBody().get("version")).intValue());

        ResponseEntity<List> history = rest.getForEntity("/resources/res-1/history", List.class);
        assertNotNull(history.getBody());
        assertEquals(2, history.getBody().size(), "both versions must be traceable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateCreateRejectedWithStableCode() {
        rest.postForEntity("/resources/res-dup", Map.of("data", "x"), Map.class);
        ResponseEntity<Map> again = rest.postForEntity("/resources/res-dup",
                Map.of("data", "y"), Map.class);
        assertEquals(HttpStatus.CONFLICT, again.getStatusCode());
        assertEquals("ALREADY_EXISTS", again.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingResourceHasStableCode() {
        ResponseEntity<Map> missing = rest.getForEntity("/resources/never-there", Map.class);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("NOT_FOUND", missing.getBody().get("code"));
    }
}
