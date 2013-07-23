package com.hp.it.perf.monitor.hub.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import com.hp.it.perf.monitor.hub.GatewayPayload;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorEvent;

class InternalHubProcessor {

	private final MonitorEndpoint endpoint;

	private List<InternalHubPublisher> publishers = new ArrayList<InternalHubPublisher>();

	private List<InternalHubSubscriber> subscribers = new ArrayList<InternalHubSubscriber>();

	private Executor executor;

	private AtomicLong seq = new AtomicLong();

	public InternalHubProcessor(MonitorEndpoint endpoint, Executor executor) {
		this.endpoint = endpoint;
		this.executor = executor;
	}

	void addPublisher(InternalHubPublisher publisher) {
		publishers.add(publisher);
	}

	void removePublisher(InternalHubPublisher publisher) {
		publishers.remove(publisher);
	}

	public MonitorEndpoint getEndpoint() {
		return endpoint;
	}

	void onData(InternalHubPublisher publisher, GatewayPayload... payloads) {
		if (payloads.length == 0) {
			return;
		}

		List<MonitorEvent> masterEvents = new ArrayList<MonitorEvent>(
				payloads.length);

		for (GatewayPayload payload : payloads) {
			MonitorEvent event = new MonitorEvent(endpoint);
			event.setContent(payload.getContent());
			event.setContentSource(payload.getContentSource());
			event.setContentType(payload.getContentType());
			event.setTime(System.currentTimeMillis());
			event.setContentId(seq.incrementAndGet());
			masterEvents.add(event);
		}

		for (InternalHubSubscriber subscriber : subscribers) {
			// avoid event content change
			List<MonitorEvent> events = new ArrayList<MonitorEvent>(
					masterEvents.size());
			for (MonitorEvent masterEvent : masterEvents) {
				MonitorEvent event = new MonitorEvent(endpoint);
				event.setContent(masterEvent.getContent());
				event.setContentSource(masterEvent.getContentSource());
				event.setContentType(masterEvent.getContentType());
				event.setTime(masterEvent.getTime());
				event.setContentId(masterEvent.getContentId());
				events.add(event);
			}
			try {
				events = subscriber.filterEvents(endpoint, events);
			} catch (Exception e) {
				continue;
			}

			if (events != null) {
				executor.execute(new SendMonitorEvent(events, subscriber));
			}
		}
	}

	void addSubscriber(InternalHubSubscriber subscriber) {
		subscribers.add(subscriber);
		subscriber.addProcessor(this);
	}

	void removeSubscriber(InternalHubSubscriber subscriber) {
		subscriber.removeProcessor(this);
		subscribers.remove(subscriber);
	}

}
