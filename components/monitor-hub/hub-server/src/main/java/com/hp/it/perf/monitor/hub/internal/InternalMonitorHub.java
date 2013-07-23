package com.hp.it.perf.monitor.hub.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import com.hp.it.perf.monitor.hub.HubEvent;
import com.hp.it.perf.monitor.hub.HubEvent.HubStatus;
import com.hp.it.perf.monitor.hub.HubPublishOption;
import com.hp.it.perf.monitor.hub.HubPublisher;
import com.hp.it.perf.monitor.hub.HubSubscribeOption;
import com.hp.it.perf.monitor.hub.HubSubscriber;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorHub;

public class InternalMonitorHub implements MonitorHub {

	private Map<MonitorEndpoint, InternalHubProcessor> endpoints = new ConcurrentHashMap<MonitorEndpoint, InternalHubProcessor>();

	private Map<HubSubscriber, InternalHubSubscriber> subscribers = new ConcurrentHashMap<HubSubscriber, InternalHubSubscriber>();

	@Override
	public MonitorEndpoint[] listEndpoints(String domainFilter) {
		List<MonitorEndpoint> ret = new ArrayList<MonitorEndpoint>();
		for (MonitorEndpoint endpoint : endpoints.keySet()) {
			if (domainFilter == null
					|| endpoint.getDomain().matches(domainFilter)) {
				ret.add(endpoint);
			}
		}
		return ret.toArray(new MonitorEndpoint[ret.size()]);
	}

	@Override
	public String[] getDomains() {
		Set<String> domains = new LinkedHashSet<String>();
		for (MonitorEndpoint endpoint : endpoints.keySet()) {
			domains.add(endpoint.getDomain());
		}
		return domains.toArray(new String[domains.size()]);
	}

	@Override
	public void subscribe(HubSubscriber subscriber, HubSubscribeOption option) {
		InternalHubSubscriber internalSubscriber = new InternalHubSubscriber(
				subscriber, option != null ? option.getMonitorFilter() : null);
		if (option != null) {
			MonitorEndpoint[] preferedEndpoints = option.getPreferedEndpoints();
			if (preferedEndpoints.length != 0) {
				// exist preferred
				for (MonitorEndpoint endpoint : preferedEndpoints) {
					InternalHubProcessor processor = endpoints.get(endpoint);
					if (processor != null) {
						processor.addSubscriber(internalSubscriber);
					}
				}
			} else {
				for (MonitorEndpoint endpoint : endpoints.keySet()) {
					if (option.isSubscribeEnabled(endpoint)) {
						InternalHubProcessor processor = endpoints
								.get(endpoint);
						processor.addSubscriber(internalSubscriber);
					}
				}
			}
		} else {
			for (MonitorEndpoint endpoint : endpoints.keySet()) {
				InternalHubProcessor processor = endpoints.get(endpoint);
				processor.addSubscriber(internalSubscriber);
			}
		}
		subscribers.put(subscriber, internalSubscriber);
		HubEvent hubEvent = new HubEvent(this, HubStatus.Connected,
				"start subscribe");
		internalSubscriber.startSubscribe(hubEvent);
	}

	@Override
	public void unsubscribe(HubSubscriber subscriber) {
		InternalHubSubscriber internalSubscriber = subscribers
				.remove(subscriber);
		if (internalSubscriber != null) {
			HubEvent hubEvent = new HubEvent(this, HubStatus.Disconnected,
					"stop subscribe");
			internalSubscriber.stopSubscribe(hubEvent);
			internalSubscriber.removeProcessors();
		}
	}

	@Override
	public HubPublisher createPublisher(MonitorEndpoint endpoint,
			HubPublishOption option) {
		// TODO put if absent
		InternalHubProcessor processor = endpoints.get(endpoint);
		if (processor == null) {
			processor = new InternalHubProcessor(endpoint, new Executor() {

				@Override
				public void execute(Runnable command) {
					command.run();
				}
			});
			endpoints.put(endpoint, processor);
		}
		InternalHubPublisher publisher = new InternalHubPublisher(processor,
				option);
		processor.addPublisher(publisher);
		return publisher;
	}

}
