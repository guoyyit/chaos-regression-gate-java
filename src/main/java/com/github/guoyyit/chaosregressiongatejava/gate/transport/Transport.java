package com.github.guoyyit.chaosregressiongatejava.gate.transport;

/** Client-side transport boundary. */
public interface Transport {
    TransportResponse send(TransportRequest request);
}
