package com.github.guoyyit.chaosregressiongatejava.gate.baseline;

/** A single difference between baseline and actual trace. */
public record TraceDiff(int entryIndex, String field, String baselineValue, String actualValue,
                        boolean volatileField) {
}
