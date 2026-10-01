package com.github.guoyyit.chaosregressiongatejava.gate.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Trace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Loads and stores baseline traces, one file per scenario and seed. */
public class BaselineStore {

    private final Path baselinesDir;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public BaselineStore(Path baselinesDir) {
        this.baselinesDir = baselinesDir;
    }

    public Optional<Trace> load(String scenario, long seed) {
        Path file = fileFor(scenario, seed);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(Files.readString(file), Trace.class));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load baseline " + file, e);
        }
    }

    public void save(Trace trace) {
        Path file = fileFor(trace.scenario(), trace.seed());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(trace));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to save baseline " + file, e);
        }
    }

    public boolean exists(String scenario, long seed) {
        return Files.exists(fileFor(scenario, seed));
    }

    private Path fileFor(String scenario, long seed) {
        return baselinesDir.resolve(scenario + "-seed-" + seed + ".baseline.json");
    }
}
