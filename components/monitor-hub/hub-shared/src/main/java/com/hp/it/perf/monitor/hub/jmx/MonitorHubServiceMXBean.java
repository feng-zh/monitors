package com.hp.it.perf.monitor.hub.jmx;

import com.hp.it.perf.monitor.hub.MonitorEndpoint;

public interface MonitorHubServiceMXBean {

	public MonitorEndpoint[] listEndpoints(String domainFilter);

	public String[] getDomains();

}
