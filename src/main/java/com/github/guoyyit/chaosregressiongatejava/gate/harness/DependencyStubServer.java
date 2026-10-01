package com.github.guoyyit.chaosregressiongatejava.gate.harness;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Deterministic in-process stub of the downstream audit dependency.
 *  Every audit call succeeds with a fixed response. */
public class DependencyStubServer implements AutoCloseable {

    private final HttpServer server;

    private DependencyStubServer(HttpServer server) {
        this.server = server;
    }

    public static DependencyStubServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/audit", exchange -> {
                byte[] body = "{\"status\":\"AUDITED\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return new DependencyStubServer(server);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to start dependency stub", e);
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
