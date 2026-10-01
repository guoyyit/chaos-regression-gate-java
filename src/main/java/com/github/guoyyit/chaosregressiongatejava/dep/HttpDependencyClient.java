package com.github.guoyyit.chaosregressiongatejava.dep;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** HTTP implementation of the dependency client. */
public class HttpDependencyClient implements DependencyClient {

    private final String baseUrl;
    private final HttpClient httpClient;

    public HttpDependencyClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void audit(String resourceId, long version, String operation) {
        String payload = "{\"resourceId\":\"" + resourceId + "\",\"version\":" + version
                + ",\"operation\":\"" + operation + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/audit"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new DependencyException("dependency rejected audit, status " + response.statusCode());
            }
        } catch (IOException e) {
            throw new DependencyException("dependency unreachable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyException("interrupted while calling dependency");
        }
    }
}
