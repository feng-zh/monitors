package com.hp.it.perf.monitor.hub;

public interface MonitorHub {

	public MonitorEndpoint[] listEndpoints(String domainFilter);

	public void subscribe(HubSubscriber subscriber, HubSubscribeOption option);

	public void unsubscribe(HubSubscriber subscriber);

	// TODO support endpoint change listener

}
