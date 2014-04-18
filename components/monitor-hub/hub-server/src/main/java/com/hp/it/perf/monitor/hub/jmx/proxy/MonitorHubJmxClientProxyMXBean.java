package com.hp.it.perf.monitor.hub.jmx.proxy;

import javax.management.ObjectName;

import com.hp.it.perf.monitor.hub.MonitorEndpoint;

public interface MonitorHubJmxClientProxyMXBean {

	public String getJmxServiceURL();

	public String getJmxUserName();

	public ObjectName getHubObjectName();

	public void destroy();

	public MonitorEndpoint[] listEndpoints(String domainFilter);

	public String[] getDomains();

	public ObjectName subscribeSingle(MonitorEndpoint endpoint);

}
