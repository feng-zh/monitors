package com.hp.it.perf.monitor.hub.internal;

import java.util.ArrayList;
import java.util.List;

import com.hp.it.perf.monitor.hub.HubEvent;
import com.hp.it.perf.monitor.hub.HubSubscribeOption;
import com.hp.it.perf.monitor.hub.HubSubscriber;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorEvent;
import com.hp.it.perf.monitor.hub.MonitorFilter;
import com.hp.it.perf.monitor.hub.support.DefaultHubSubscribeOption;

class InternalHubSubscriber implements HubSubscriber {

	// keep use ArrayList
	private List<InternalHubProcessor> processors = new ArrayList<InternalHubProcessor>();

	private boolean running = false;

	private final HubSubscriber subscriber;

	private final HubSubscribeOption option;

	public InternalHubSubscriber(HubSubscriber subscriber,
			HubSubscribeOption option) {
		this.subscriber = subscriber;
		this.option = option;
	}

	@Override
	public void onData(MonitorEvent... events) {
		if (!running)
			return;
		DefaultHubSubscribeOption.batchOnData(subscriber, option, events);
	}

	@Override
	public void onHubEvent(HubEvent event) {
		subscriber.onHubEvent(event);
	}

	public void startSubscribe(HubEvent event) {
		running = true;
		subscriber.onHubEvent(event);
	}

	public void stopSubscribe(HubEvent event) {
		subscriber.onHubEvent(event);
		running = false;
	}

	void removeProcessors() {
		for (InternalHubProcessor processor : new ArrayList<InternalHubProcessor>(
				processors)) {
			processor.removeSubscriber(this);
		}
	}

	void addProcessor(InternalHubProcessor processor) {
		processors.add(processor);
	}

	void removeProcessor(InternalHubProcessor processor) {
		processors.remove(processor);
	}

	public List<MonitorEvent> filterEvents(MonitorEndpoint endpoint,
			List<MonitorEvent> events) {
		if (!running) {
			return null;
		}
		if (option == null || option.getMonitorFilter() == null) {
			return events;
		}
		MonitorFilter filter = option.getMonitorFilter();
		List<MonitorEvent> eventList = new ArrayList<MonitorEvent>(
				events.size());
		for (MonitorEvent event : events) {
			if (filter.accept(endpoint, event)) {
				eventList.add(event);
			}
		}
		return eventList;
	}
}
