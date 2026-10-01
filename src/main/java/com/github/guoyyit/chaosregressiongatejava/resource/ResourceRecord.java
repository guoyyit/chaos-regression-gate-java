package com.github.guoyyit.chaosregressiongatejava.resource;

/** A single version of a stateful resource. */
public record ResourceRecord(String id, long version, String data) {
}
