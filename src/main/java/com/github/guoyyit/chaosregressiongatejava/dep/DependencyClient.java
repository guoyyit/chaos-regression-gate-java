package com.github.guoyyit.chaosregressiongatejava.dep;

/** Client boundary to the downstream audit dependency. */
public interface DependencyClient {

    /** Notify the dependency about a mutation. Throws DependencyException on failure. */
    void audit(String resourceId, long version, String operation);
}
