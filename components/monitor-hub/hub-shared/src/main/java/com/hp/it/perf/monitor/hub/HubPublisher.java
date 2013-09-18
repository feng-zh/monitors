package com.hp.it.perf.monitor.hub;

import java.io.Closeable;

public interface HubPublisher extends Closeable {

	public void post(GatewayPayload... payloads);

	public void update(GatewayStatus status);

	public void close();

}
