package com.hp.it.perf.monitor.hub.support;

import com.hp.it.perf.monitor.hub.HubSubscribeOption;
import com.hp.it.perf.monitor.hub.HubSubscriber;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorEvent;
import com.hp.it.perf.monitor.hub.MonitorFilter;

public class DefaultHubSubscribeOption implements HubSubscribeOption {

	private MonitorEndpoint[] endpoints;

	private MonitorFilter filter;

	private int batchSize = 0;

	public DefaultHubSubscribeOption(MonitorEndpoint... endpoints) {
		this.endpoints = endpoints;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
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

	public static void batchOnData(HubSubscriber subscriber,
			HubSubscribeOption option, MonitorEvent[] events) {
		if (option == null || option.getBatchSize() <= 0
				|| option.getBatchSize() >= events.length) {
			subscriber.onData(events);
		} else {
			int offset = 0;
			while (offset < events.length) {
				MonitorEvent[] batchEvents = new MonitorEvent[Math.min(
						events.length - offset, option.getBatchSize())];
				System.arraycopy(events, offset, batchEvents, 0,
						batchEvents.length);
				subscriber.onData(batchEvents);
				offset += batchEvents.length;
			}
		}
	}

	@Override
	public int getBatchSize() {
		return batchSize;
	}

}
