package com.github.guoyyit.chaosregressiongatejava.gate.transport;

import com.github.guoyyit.chaosregressiongatejava.gate.FaultType;
import com.github.guoyyit.chaosregressiongatejava.gate.model.FaultSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Transport decorator applying client-boundary faults deterministically.
 *  Faults never sleep or drop randomly: TIMEOUT and CONNECTION_RESET synthesize
 *  stable error responses, DUPLICATE issues the request twice, DELAY is virtual
 *  and only reported to the trace. */
public class FaultInjectingTransport implements Transport {

    /** Synthetic status codes for client-observed faults (stable, never from the server). */
    public static final int STATUS_CLIENT_TIMEOUT = 598;
    public static final int STATUS_CONNECTION_RESET = 597;

    private final Transport delegate;

    public FaultInjectingTransport(Transport delegate) {
        this.delegate = delegate;
    }

    /** Sends the request applying the given faults; reports applied fault names
     *  to the listener. Returns one response per actual attempt (DUPLICATE -> two). */
    public List<TransportResponse> send(TransportRequest request, List<FaultSpec> faults,
                                        Consumer<String> faultListener) {
        List<TransportResponse> responses = new ArrayList<>();
        boolean duplicate = false;
        for (FaultSpec fault : faults) {
            if (fault.type() == FaultType.TIMEOUT) {
                faultListener.accept("TIMEOUT");
                return List.of(new TransportResponse(STATUS_CLIENT_TIMEOUT,
                        "{\"code\":\"CLIENT_TIMEOUT\"}"));
            }
            if (fault.type() == FaultType.CONNECTION_RESET) {
                faultListener.accept("CONNECTION_RESET");
                return List.of(new TransportResponse(STATUS_CONNECTION_RESET,
                        "{\"code\":\"CONNECTION_RESET\"}"));
            }
            if (fault.type() == FaultType.DELAY) {
                faultListener.accept("DELAY(" + fault.delayMs() + ")");
            }
            if (fault.type() == FaultType.DUPLICATE) {
                faultListener.accept("DUPLICATE");
                duplicate = true;
            }
        }
        responses.add(delegate.send(request));
        if (duplicate) {
            responses.add(delegate.send(request));
        }
        return responses;
    }

    @Override
    public TransportResponse send(TransportRequest request) {
        return delegate.send(request);
    }
}
