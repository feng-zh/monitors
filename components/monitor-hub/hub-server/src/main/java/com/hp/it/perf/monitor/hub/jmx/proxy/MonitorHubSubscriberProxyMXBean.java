package com.hp.it.perf.monitor.hub.jmx.proxy;

import java.util.Date;

public interface MonitorHubSubscriberProxyMXBean {

	public void resume();

	public void pause();

	public String getStatus();

	public void destroy();

	public Date getStartedDate();

	public long getCount();

}
