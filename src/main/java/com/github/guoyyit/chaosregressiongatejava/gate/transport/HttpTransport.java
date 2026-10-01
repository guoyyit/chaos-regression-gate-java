package com.github.guoyyit.chaosregressiongatejava.gate.transport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Real HTTP transport to the service under test. */
public class HttpTransport implements Transport {

    private final String baseUrl;
    private final HttpClient client;

    public HttpTransport(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public TransportResponse send(TransportRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + request.path()))
                .timeout(Duration.ofSeconds(30));
        String method = request.method().toUpperCase();
        HttpRequest.BodyPublisher body = request.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(request.body());
        switch (method) {
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            case "POST" -> builder.POST(body);
            case "PUT" -> builder.PUT(body);
            default -> builder.method(method, body);
        }
        if (request.body() != null) {
            builder.header("Content-Type", "application/json");
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new TransportResponse(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("HTTP call failed: " + request.method() + " " + request.path(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during HTTP call", e);
        }
    }
}
