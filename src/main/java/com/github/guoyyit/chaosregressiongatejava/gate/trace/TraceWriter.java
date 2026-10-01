package com.github.guoyyit.chaosregressiongatejava.gate.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Writes normalized traces to disk. The trace format contains no ports,
 * timestamps or random identifiers, so two runs with the same scenario and seed
 * produce byte-identical files.
 */
public class TraceWriter {
    private final ObjectMapper mapper;

    public TraceWriter() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public void write(Trace trace, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            mapper.writeValue(file, trace);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write trace to " + file, e);
        }
    }

    public Trace read(File file) {
        try {
            return mapper.readValue(file, Trace.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read trace from " + file, e);
        }
    }

    public byte[] toBytes(Trace trace) {
        try {
            return mapper.writeValueAsBytes(trace);
        } catch (IOException e) {
            throw new IllegalStateException("failed to serialize trace", e);
        }
    }
}
