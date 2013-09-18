package com.hp.it.perf.monitor.hub.jmx;

public interface MonitorHubEndpointServiceMXBean {

	public String NOTIFICATION_MONITOR_EVENT = "MONITOR_EVENT";

	public String NOTIFICATION_HUB_EVENT = "HUB_EVENT";
	
	public String NOTIFICATION_COMPRESSED_EVENT = "COMPRESSED_EVENT";

	public long getDataCount();

	public String getEndpointDomain();

	public String getEndpointName();

	public void setNotificationOpenTypeEnabled(boolean enable);

	public boolean isNotificationOpenTypeEnabled();

	public void setNotificationCompressEnabled(boolean enable);

	public boolean isNotificationCompressEnabled();

}
