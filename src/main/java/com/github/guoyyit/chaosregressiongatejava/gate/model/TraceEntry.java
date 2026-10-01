package com.github.guoyyit.chaosregressiongatejava.gate.model;

import java.util.List;
import java.util.Map;

/** One normalized trace event. Contains no ports, timestamps or random ids;
 *  volatile fields (allowed to vary) are kept in a separate map. */
public record TraceEntry(int seq, String stepId, String action, String request,
                         Integer status, String code, String body,
                         List<String> faultsApplied, Map<String, Object> volatileFields) {
}
