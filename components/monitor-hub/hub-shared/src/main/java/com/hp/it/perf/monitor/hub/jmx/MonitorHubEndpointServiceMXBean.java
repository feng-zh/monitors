package com.hp.it.perf.monitor.hub.jmx;

import com.hp.it.perf.monitor.hub.GatewayPayload;

public interface MonitorHubEndpointServiceMXBean {

	public long getDataCount();

	public String getEndpointDomain();

	public String getEndpointName();

	public void publish(GatewayPayload... payloads);

}
