package com.hp.it.perf.monitor.hub;

import java.io.Serializable;

public interface HubSubscribeOption extends Serializable {

	public boolean isSubscribeEnabled(MonitorEndpoint endpoint);

	public MonitorEndpoint[] getPreferedEndpoints();

	public MonitorFilter getMonitorFilter();

}
