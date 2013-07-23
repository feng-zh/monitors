package com.hp.it.perf.monitor.hub.internal;

import java.util.ArrayList;
import java.util.List;

import com.hp.it.perf.monitor.hub.HubEvent;
import com.hp.it.perf.monitor.hub.HubSubscriber;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorEvent;
import com.hp.it.perf.monitor.hub.MonitorFilter;

class InternalHubSubscriber implements HubSubscriber {

	// keep use ArrayList
	private List<InternalHubProcessor> processors = new ArrayList<InternalHubProcessor>();

	private boolean running = false;

	private final HubSubscriber subscriber;

	private final MonitorFilter filter;

	public InternalHubSubscriber(HubSubscriber subscriber, MonitorFilter filter) {
		this.subscriber = subscriber;
		this.filter = filter;
	}

	@Override
	public void onData(MonitorEvent event) {
		if (!running)
			return;
		subscriber.onData(event);
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
		if (filter == null) {
			return events;
		}
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
