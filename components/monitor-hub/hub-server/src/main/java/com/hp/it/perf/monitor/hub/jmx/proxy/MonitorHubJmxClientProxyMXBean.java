package com.hp.it.perf.monitor.hub.jmx.proxy;

import javax.management.ObjectName;

public interface MonitorHubJmxClientProxyMXBean {

	public String getJmxServiceURL();

	public String getJmxUserName();

	public ObjectName getHubObjectName();

	public void start();

	public void stop();
	
	public String getStatus();

	public void destroy();

	public String[] listEndpoints(String domainFilter);

	public String[] getDomains();

	public ObjectName createSubscriber(String endpoint);

	public ObjectName[] listSubscribers(String endpoint);

}
