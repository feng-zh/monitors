package com.hp.it.perf.monitor.hub;

public interface MonitorHub {

	// null filter: return all
	public MonitorEndpoint[] listEndpoints(String domainFilter);

	public String[] getDomains();

	public void subscribe(HubSubscriber subscriber, HubSubscribeOption option);

	public void unsubscribe(HubSubscriber subscriber);

	// TODO support endpoint change listener

	public HubPublisher createPublisher(MonitorEndpoint endpoint,
			HubPublishOption option);

}
