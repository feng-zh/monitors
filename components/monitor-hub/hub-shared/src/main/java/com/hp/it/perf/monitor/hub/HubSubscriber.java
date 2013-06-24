package com.hp.it.perf.monitor.hub;

public interface HubSubscriber {

	public void onData(MonitorEvent event);

	public void onHubEvent(HubEvent event);

}
