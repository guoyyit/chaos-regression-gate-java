package com.github.guoyyit.chaosregressiongatejava.gate.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.guoyyit.chaosregressiongatejava.gate.FaultType;
import com.github.guoyyit.chaosregressiongatejava.gate.model.DepFaultSpec;
import com.github.guoyyit.chaosregressiongatejava.gate.model.FaultSpec;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Step;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Trace;
import com.github.guoyyit.chaosregressiongatejava.gate.model.TraceEntry;
import com.github.guoyyit.chaosregressiongatejava.gate.transport.FaultInjectingTransport;
import com.github.guoyyit.chaosregressiongatejava.gate.transport.HttpTransport;
import com.github.guoyyit.chaosregressiongatejava.gate.transport.TransportRequest;
import com.github.guoyyit.chaosregressiongatejava.gate.transport.TransportResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Deterministic single-scenario executor: interleaves streams by seed,
 *  applies faults, records a normalized trace to disk. */
public class ScenarioRunner {

    private static final Pattern PORT_PATTERN = Pattern.compile("localhost:\\d+");
    private static final Pattern ISO_TIME_PATTERN =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[\\d.]*Z?");
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final Path runDir;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public ScenarioRunner(Path runDir) {
        this.runDir = runDir;
    }

    /** Runs the scenario once with the given seed against the given base URL. */
    public Trace run(Scenario scenario, long seed, String baseUrl) {
        FaultInjectingTransport transport = new FaultInjectingTransport(new HttpTransport(baseUrl));
        List<TraceEntry> entries = new ArrayList<>();
        Deque<TraceEntry> deferred = new ArrayDeque<>();

        transport.send(new TransportRequest("POST", "/__control/reset", null), List.of(), f -> { });
        installDependencyFaults(scenario, transport, entries);

        Map<String, Deque<Step>> streams = groupByStream(scenario.steps());
        Map<String, Long> streamReadyAt = new LinkedHashMap<>();
        streams.keySet().forEach(s -> streamReadyAt.put(s, 0L));
        Random random = new Random(seed);
        long virtualClock = 0;

        while (!streams.isEmpty()) {
            List<String> ready = readyStreams(streams, streamReadyAt, virtualClock);
            if (ready.isEmpty()) {
                virtualClock = streamReadyAt.entrySet().stream()
                        .filter(e -> streams.containsKey(e.getKey()))
                        .mapToLong(Map.Entry::getValue).min().orElse(virtualClock);
                ready = readyStreams(streams, streamReadyAt, virtualClock);
            }
            String stream = ready.get(random.nextInt(ready.size()));
            Deque<Step> queue = streams.get(stream);
            Step step = queue.poll();
            if (queue.isEmpty()) {
                streams.remove(stream);
            }
            virtualClock++;

            List<String> applied = new ArrayList<>();
            long delayMs = delayOf(step.faults());
            if (delayMs > 0) {
                streamReadyAt.put(stream, virtualClock + delayMs);
            }

            List<TransportResponse> responses = transport.send(
                    new TransportRequest(step.method(), step.path(), step.body()),
                    step.faults() == null ? List.of() : step.faults(),
                    applied::add);

            List<TraceEntry> stepEntries = new ArrayList<>();
            for (int attempt = 0; attempt < responses.size(); attempt++) {
                TransportResponse response = responses.get(attempt);
                String requestLabel = step.method().toUpperCase() + " " + step.path()
                        + (attempt > 0 ? "#" + (attempt + 1) : "");
                stepEntries.add(new TraceEntry(0, step.id(), "RESPONSE", requestLabel,
                        response.status(), extractCode(response.body()), normalize(response.body()),
                        List.copyOf(applied), Map.of("virtualClock", virtualClock)));
            }
            boolean outOfOrder = hasFault(step.faults(), FaultType.OUT_OF_ORDER);
            if (outOfOrder) {
                deferred.addAll(stepEntries);
            } else {
                flushDeferred(deferred, entries);
                entries.addAll(stepEntries);
            }
        }
        flushDeferred(deferred, entries);

        List<TraceEntry> sequenced = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            TraceEntry e = entries.get(i);
            sequenced.add(new TraceEntry(i, e.stepId(), e.action(), e.request(), e.status(),
                    e.code(), e.body(), e.faultsApplied(), e.volatileFields()));
        }
        Trace trace = new Trace(scenario.name(), seed, sequenced);
        persist(trace);
        return trace;
    }

    private void installDependencyFaults(Scenario scenario, FaultInjectingTransport transport,
                                         List<TraceEntry> entries) {
        Map<Long, String> faults = new TreeMap<>();
        for (DepFaultSpec spec : scenario.dependencyFaults() == null ? List.<DepFaultSpec>of() : scenario.dependencyFaults()) {
            faults.put((long) spec.callIndex(), spec.type().name());
        }
        try {
            String body = mapper.writeValueAsString(Map.of("faults", faults));
            transport.send(new TransportRequest("POST", "/__control/faults", body),
                    List.of(), f -> { });
        } catch (Exception e) {
            throw new IllegalStateException("failed to install dependency fault plan", e);
        }
        entries.add(new TraceEntry(0, "__control", "DEP_FAULTS_INSTALLED",
                "POST /__control/faults", 200, null, faults.toString(), List.of(), Map.of()));
    }

    private Map<String, Deque<Step>> groupByStream(List<Step> steps) {
        Map<String, Deque<Step>> streams = new LinkedHashMap<>();
        for (Step step : steps) {
            String stream = step.stream() == null ? "main" : step.stream();
            streams.computeIfAbsent(stream, k -> new ArrayDeque<>()).add(step);
        }
        return streams;
    }

    private List<String> readyStreams(Map<String, Deque<Step>> streams,
                                      Map<String, Long> streamReadyAt, long virtualClock) {
        List<String> ready = new ArrayList<>();
        for (String stream : streams.keySet()) {
            if (streamReadyAt.getOrDefault(stream, 0L) <= virtualClock) {
                ready.add(stream);
            }
        }
        return ready;
    }

    private long delayOf(List<FaultSpec> faults) {
        if (faults == null) {
            return 0;
        }
        return faults.stream()
                .filter(f -> f.type() == FaultType.DELAY && f.delayMs() != null)
                .mapToLong(FaultSpec::delayMs).sum();
    }

    private boolean hasFault(List<FaultSpec> faults, FaultType type) {
        return faults != null && faults.stream().anyMatch(f -> f.type() == type);
    }

    private void flushDeferred(Deque<TraceEntry> deferred, List<TraceEntry> entries) {
        while (!deferred.isEmpty()) {
            entries.add(deferred.poll());
        }
    }

    private String extractCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(body);
            JsonNode code = node.get("code");
            return code == null ? null : code.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /** Removes ports, timestamps and random identifiers from recorded content. */
    String normalize(String content) {
        if (content == null) {
            return null;
        }
        String result = PORT_PATTERN.matcher(content).replaceAll("localhost:<PORT>");
        result = ISO_TIME_PATTERN.matcher(result).replaceAll("<TIMESTAMP>");
        result = UUID_PATTERN.matcher(result).replaceAll("<UUID>");
        return result;
    }

    private void persist(Trace trace) {
        try {
            Path file = runDir.resolve("traces")
                    .resolve(trace.scenario() + "-seed-" + trace.seed() + ".trace.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(trace));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to persist trace", e);
        }
    }
}
