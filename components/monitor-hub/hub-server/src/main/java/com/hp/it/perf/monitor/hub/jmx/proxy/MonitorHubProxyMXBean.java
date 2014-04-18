package com.hp.it.perf.monitor.hub.jmx.proxy;

import java.util.List;

import javax.management.ObjectName;

public interface MonitorHubProxyMXBean {

	public ObjectName createHubJmxClient(String jmxServiceURL, String username,
			String password, ObjectName hubObjectName);

	public List<ObjectName> listHubJmxClient();

}
