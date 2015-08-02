package com.hp.it.perf.monitor.hub.jmx;

import com.hp.it.perf.monitor.hub.MonitorEndpoint;

public interface MonitorHubServiceMXBean {

	public MonitorEndpoint[] listEndpoints(String domainFilter);

	public String[] getDomains();

	public void setNotificationOpenTypeDefault(boolean enable);

	public boolean isNotificationOpenTypeDefault();

	public void setNotificationCompressDefault(boolean enable);

	public boolean isNotificationCompressDefault();

}
