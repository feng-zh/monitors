package com.hp.it.perf.monitor.hub.support;

import com.hp.it.perf.monitor.hub.HubSubscribeOption;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorFilter;

public class DefaultHubSubscribeOption implements HubSubscribeOption {

	private MonitorEndpoint[] endpoints;

	private MonitorFilter filter;

	public DefaultHubSubscribeOption(MonitorEndpoint... endpoints) {
		this.endpoints = endpoints;
	}

	@Override
	public boolean isSubscribeEnabled(MonitorEndpoint endpoint) {
		for (MonitorEndpoint me : endpoints) {
			if (me.equals(endpoint)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public MonitorEndpoint[] getPreferedEndpoints() {
		return endpoints;
	}

	@Override
	public MonitorFilter getMonitorFilter() {
		return filter;
	}

	public void setMonitorFilter(MonitorFilter filter) {
		this.filter = filter;
	}

}
