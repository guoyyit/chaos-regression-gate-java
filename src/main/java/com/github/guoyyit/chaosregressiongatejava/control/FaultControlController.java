package com.github.guoyyit.chaosregressiongatejava.control;

import com.github.guoyyit.chaosregressiongatejava.dep.DependencyFaultPlan;
import com.github.guoyyit.chaosregressiongatejava.resource.ResourceStore;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Control endpoint used by the gate to install dependency-boundary fault plans
 *  and to reset all mutable state between deterministic runs. */
@RestController
@RequestMapping("/__control")
public class FaultControlController {

    private final DependencyFaultPlan plan;
    private final ResourceStore store;

    public FaultControlController(DependencyFaultPlan plan, ResourceStore store) {
        this.plan = plan;
        this.store = store;
    }

    public record FaultPlanRequest(Map<Long, String> faults) {
    }

    @PostMapping("/faults")
    public Map<String, String> setFaults(@RequestBody FaultPlanRequest request) {
        plan.setFaults(request.faults() == null ? Map.of() : request.faults());
        return Map.of("status", "INSTALLED");
    }

    /** Clears all mutable state so a scenario run starts from a clean slate. */
    @PostMapping("/reset")
    public Map<String, String> reset() {
        plan.reset();
        store.reset();
        return Map.of("status", "RESET");
    }
}
