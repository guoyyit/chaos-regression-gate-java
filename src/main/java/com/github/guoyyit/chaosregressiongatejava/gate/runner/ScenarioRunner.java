package com.github.guoyyit.chaosregressiongatejava.gate.runner;

import com.github.guoyyit.chaosregressiongatejava.dependency.FaultPlan;
import com.github.guoyyit.chaosregressiongatejava.gate.fault.FaultBoundary;
import com.github.guoyyit.chaosregressiongatejava.gate.fault.FaultPlanGenerator;
import com.github.guoyyit.chaosregressiongatejava.gate.fault.FaultType;
import com.github.guoyyit.chaosregressiongatejava.gate.fault.InjectedFault;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.ScenarioRequest;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.RequestTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.Trace;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Runs a scenario deterministically and records a normalized trace.
 * <p>
 * Determinism is achieved by:
 * <ul>
 *   <li>Wave-based scheduling derived from the dependency DAG (dependsOn edges
 *       plus implicit edges from OUT_OF_ORDER faults).</li>
 *   <li>Seeded tie-breaking of dispatch order within a wave.</li>
 *   <li>Resource-level serialization of concurrent writes to the same resource
 *       (configurable via {@code serializeResourceAccess}).</li>
 * </ul>
 * The same scenario and seed always produce the same trace.
 */
public class ScenarioRunner {
    private final long timeoutMs;
    private final long scenarioTimeoutMs;

    public ScenarioRunner() {
        this(1000, 60000);
    }

    public ScenarioRunner(long timeoutMs, long scenarioTimeoutMs) {
        this.timeoutMs = timeoutMs;
        this.scenarioTimeoutMs = scenarioTimeoutMs;
    }

    public RunResult run(Scenario scenario, File runDir) {
        ServiceLauncher.LaunchedService service = null;
        runDir.mkdirs();
        try {
            FaultPlanGenerator gen = new FaultPlanGenerator();
            List<InjectedFault> clientFaults = gen.clientFaults(scenario);
            FaultPlan depPlan = gen.dependencyFaultPlan(scenario);

            Map<String, List<InjectedFault>> faultsByRequest = new HashMap<>();
            for (InjectedFault f : clientFaults) {
                faultsByRequest.computeIfAbsent(f.getRequestId(), k -> new ArrayList<>()).add(f);
            }

            Map<String, Set<String>> deps = buildDeps(scenario, clientFaults);
            Map<String, Integer> wave = computeWaves(deps);

            Random rng = new Random(scenario.getSeed());
            Map<String, Long> priority = new HashMap<>();
            for (ScenarioRequest r : scenario.getRequests()) {
                priority.put(r.getId(), rng.nextLong());
            }

            service = new ServiceLauncher().launch(runDir, depPlan);
            String baseUrl = "http://localhost:" + service.getPort();

            // Deterministic resource id hint based on request position in the scenario.
            Map<String, Integer> requestIndex = new HashMap<>();
            for (int i = 0; i < scenario.getRequests().size(); i++) {
                requestIndex.put(scenario.getRequests().get(i).getId(), i);
            }

            boolean serialize = scenario.getSerializeResourceAccess() == null || scenario.getSerializeResourceAccess();
            Map<String, Object> resourceLocks = new ConcurrentHashMap<>();
            Map<String, GateHttpClient.ClientResult> results = new ConcurrentHashMap<>();
            Map<String, Integer> dispatchIndex = new ConcurrentHashMap<>();
            Map<String, Integer> completionIndex = new ConcurrentHashMap<>();

            int maxWave = wave.values().stream().max(Integer::compareTo).orElse(0);
            int[] globalIndex = {0};
            for (int w = 0; w <= maxWave; w++) {
                final int waveNo = w;
                List<ScenarioRequest> waveRequests = scenario.getRequests().stream()
                        .filter(r -> wave.get(r.getId()) != null && wave.get(r.getId()) == waveNo)
                        .sorted((a, b) -> {
                            Long pa = priority.get(a.getId());
                            Long pb = priority.get(b.getId());
                            return Long.compare(pa != null ? pa : 0L, pb != null ? pb : 0L);
                        })
                        .collect(Collectors.toList());

                // Assign deterministic dispatch indices before execution.
                for (ScenarioRequest r : waveRequests) {
                    dispatchIndex.put(r.getId(), globalIndex[0]++);
                }

                ExecutorService pool = Executors.newFixedThreadPool(Math.max(waveRequests.size(), 1));
                try {
                    List<Future<?>> futures = new ArrayList<>();
                    for (ScenarioRequest r : waveRequests) {
                        futures.add(pool.submit(() -> {
                            String resourceKey = resourceKey(r.getPath());
                            Object lock = (serialize && resourceKey != null)
                                    ? resourceLocks.computeIfAbsent(resourceKey, k -> new Object())
                                    : null;
                            Runnable task = () -> {
                                GateHttpClient client = new GateHttpClient(baseUrl, timeoutMs);
                                String resourceIdHint = r.getPath().equals("/api/resources")
                                        ? "r-" + (requestIndex.get(r.getId()) + 1) : null;
                                GateHttpClient.ClientResult res = client.send(
                                        r.getMethod(), r.getPath(), r.getBody(), r.getId(),
                                        faultsByRequest.getOrDefault(r.getId(), Collections.emptyList()),
                                        resourceIdHint);
                                results.put(r.getId(), res);
                                completionIndex.put(r.getId(), dispatchIndex.get(r.getId()));
                            };
                            if (lock != null) {
                                synchronized (lock) {
                                    task.run();
                                }
                            } else {
                                task.run();
                            }
                        }));
                    }
                    for (Future<?> f : futures) {
                        f.get(scenarioTimeoutMs, TimeUnit.MILLISECONDS);
                    }
                } finally {
                    pool.shutdownNow();
                }
            }

            Trace trace = buildTrace(scenario, wave, dispatchIndex, completionIndex, results, faultsByRequest);
            RunResult runResult = new RunResult();
            runResult.setSuccess(true);
            runResult.setTrace(trace);
            return runResult;
        } catch (Exception e) {
            e.printStackTrace();
            RunResult runResult = new RunResult();
            runResult.setSuccess(false);
            runResult.setFailureReason(e.getClass().getSimpleName() + ": " + e.getMessage());
            return runResult;
        } finally {
            if (service != null) {
                service.close();
            }
        }
    }

    private Map<String, Set<String>> buildDeps(Scenario scenario, List<InjectedFault> clientFaults) {
        Map<String, Set<String>> deps = new HashMap<>();
        for (ScenarioRequest r : scenario.getRequests()) {
            deps.put(r.getId(), new HashSet<>(r.getDependsOn()));
        }
        for (InjectedFault f : clientFaults) {
            if (f.getType() == FaultType.OUT_OF_ORDER) {
                deps.get(f.getRequestId()).add(f.getAfterRequestId());
            }
        }
        return deps;
    }

    private Map<String, Integer> computeWaves(Map<String, Set<String>> deps) {
        Map<String, Integer> wave = new HashMap<>();
        Set<String> visited = new HashSet<>();
        for (String id : deps.keySet()) {
            computeWave(id, deps, wave, visited);
        }
        return wave;
    }

    private int computeWave(String id, Map<String, Set<String>> deps,
                            Map<String, Integer> wave, Set<String> visited) {
        if (wave.containsKey(id)) {
            return wave.get(id);
        }
        if (!visited.add(id)) {
            throw new IllegalArgumentException("cyclic dependency detected at " + id);
        }
        int maxDep = -1;
        for (String dep : deps.get(id)) {
            Integer w = wave.get(dep);
            if (w == null) {
                w = computeWave(dep, deps, wave, visited);
            }
            maxDep = Math.max(maxDep, w);
        }
        int w = maxDep + 1;
        wave.put(id, w);
        return w;
    }

    private String resourceKey(String path) {
        if (path == null) {
            return null;
        }
        // Creates use a deterministic resource id hint, so no serialization needed.
        if (path.equals("/api/resources")) {
            return null;
        }
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("resources".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }

    private Trace buildTrace(Scenario scenario, Map<String, Integer> wave,
                             Map<String, Integer> dispatchIndex, Map<String, Integer> completionIndex,
                             Map<String, GateHttpClient.ClientResult> results,
                             Map<String, List<InjectedFault>> faultsByRequest) {
        Trace trace = new Trace();
        trace.setScenario(scenario.getName());
        trace.setSeed(scenario.getSeed());
        for (ScenarioRequest r : scenario.getRequests()) {
            RequestTrace rt = new RequestTrace();
            rt.setId(r.getId());
            rt.setMethod(r.getMethod());
            rt.setPath(r.getPath());
            rt.setWave(wave.get(r.getId()));
            rt.setDispatchIndex(dispatchIndex.get(r.getId()));
            Integer ci = completionIndex.get(r.getId());
            rt.setCompletionIndex(ci != null ? ci : dispatchIndex.get(r.getId()));
            GateHttpClient.ClientResult res = results.get(r.getId());
            if (res != null) {
                rt.setAttempts(res.attempts);
                rt.setDependencyEvents(res.dependencyEvents);
            }
            List<String> faultNames = faultsByRequest.getOrDefault(r.getId(), Collections.emptyList()).stream()
                    .map(f -> f.getType().name() + ":" + f.getBoundary().name())
                    .collect(Collectors.toList());
            rt.setFaults(faultNames);
            trace.getRequests().add(rt);
        }
        return trace;
    }
}
