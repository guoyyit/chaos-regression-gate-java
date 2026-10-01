package com.github.guoyyit.chaosregressiongatejava.gate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Scenario;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Loads and saves declarative JSON scenario files. */
public class ScenarioLoader {

    private final ObjectMapper mapper;

    public ScenarioLoader() {
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Scenario load(Path file) {
        try {
            return mapper.readValue(Files.readString(file), Scenario.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load scenario " + file, e);
        }
    }

    public void save(Scenario scenario, Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(scenario));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to save scenario " + file, e);
        }
    }

    /** Loads all *.json scenarios from a directory, sorted by file name for determinism. */
    public List<Scenario> loadAll(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString()))
                    .map(this::load)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list scenarios in " + dir, e);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
