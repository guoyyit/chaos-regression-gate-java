package com.github.guoyyit.chaosregressiongatejava.gate.harness;

import com.github.guoyyit.chaosregressiongatejava.Application;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts the service under test on an isolated random port. */
public class ServerHarness implements AutoCloseable {

    private final ConfigurableApplicationContext context;

    private ServerHarness(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public static ServerHarness start(int dependencyPort) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("server.port", "0");
        properties.put("dependency.base-url", "http://127.0.0.1:" + dependencyPort);
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "WARN");
        ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.SERVLET)
                .properties(properties)
                .run();
        return new ServerHarness(context);
    }

    public int port() {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @Override
    public void close() {
        context.close();
    }
}
