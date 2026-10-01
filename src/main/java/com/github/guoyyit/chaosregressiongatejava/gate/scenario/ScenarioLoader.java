package com.github.guoyyit.chaosregressiongatejava.gate.scenario;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Loads declarative scenario files (JSON).
 */
public class ScenarioLoader {
    private final ObjectMapper mapper;

    public ScenarioLoader() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Scenario load(File file) {
        try {
            Scenario scenario = mapper.readValue(file, Scenario.class);
            if (scenario.getName() == null || scenario.getName().isBlank()) {
                scenario.setName(file.getName().replaceAll("\\.json$", ""));
            }
            validate(scenario);
            return scenario;
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to load scenario from " + file, e);
        }
    }

    public List<Scenario> loadDir(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return new ArrayList<>();
        }
        List<Scenario> scenarios = new ArrayList<>();
        for (File f : files) {
            scenarios.add(load(f));
        }
        scenarios.sort(Comparator.comparing(Scenario::getName));
        return scenarios;
    }

    private void validate(Scenario scenario) {
        if (scenario.getRequests() == null || scenario.getRequests().isEmpty()) {
            throw new IllegalArgumentException("scenario '" + scenario.getName() + "' has no requests");
        }
        for (ScenarioRequest r : scenario.getRequests()) {
            if (r.getId() == null || r.getId().isBlank()) {
                throw new IllegalArgumentException("scenario '" + scenario.getName() + "' has a request without id");
            }
            if (r.getMethod() == null || r.getPath() == null) {
                throw new IllegalArgumentException("scenario '" + scenario.getName() + "' request " + r.getId() + " missing method or path");
            }
        }
        for (ScenarioFault f : scenario.getFaults()) {
            if (f.getType() == null || f.getBoundary() == null || f.getRequest() == null) {
                throw new IllegalArgumentException("scenario '" + scenario.getName() + "' fault missing type/boundary/request");
            }
        }
    }
}
