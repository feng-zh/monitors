package com.hp.it.perf.monitor.hub.support;

import com.hp.it.perf.monitor.hub.HubSubscribeOption;
import com.hp.it.perf.monitor.hub.HubSubscriber;
import com.hp.it.perf.monitor.hub.HubSubscriberHandler;
import com.hp.it.perf.monitor.hub.MonitorHub;

public class DefaultHubSubscriberHandler implements HubSubscriberHandler {

	private HubSubscriber subscriber;

	private HubSubscribeOption option;

	private MonitorHub hub;

	public DefaultHubSubscriberHandler(MonitorHub hub,
			HubSubscriber subscriber, HubSubscribeOption option) {
		this.hub = hub;
		this.subscriber = subscriber;
		this.option = option;
	}

	@Override
	public HubSubscriber getSubscriber() {
		return subscriber;
	}

	@Override
	public HubSubscribeOption getOption() {
		return option;
	}

}
