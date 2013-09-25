package com.hp.it.perf.monitor.hub.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import com.hp.it.perf.monitor.hub.GatewayPayload;
import com.hp.it.perf.monitor.hub.GatewayStatus;
import com.hp.it.perf.monitor.hub.HubEvent;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorEvent;

class InternalHubProcessor {

	private final MonitorEndpoint endpoint;

	private List<InternalHubPublisher> publishers = new ArrayList<InternalHubPublisher>();

	private List<InternalHubSubscriber> subscribers = new CopyOnWriteArrayList<InternalHubSubscriber>();

	private Executor executor;

	private InternalMonitorHub monitorHub;

	private static class SendMonitorEvent implements Runnable {

		private final List<MonitorEvent> events;
		private final InternalHubSubscriber subscriber;

		SendMonitorEvent(List<MonitorEvent> events,
				InternalHubSubscriber subscriber) {
			this.events = events;
			this.subscriber = subscriber;
		}

		@Override
		public void run() {
			for (MonitorEvent event : events) {
				subscriber.onData(event);
			}
		}

	}

	private static class SendHubEvent implements Runnable {

		private HubEvent event;
		private InternalHubSubscriber subscriber;

		SendHubEvent(HubEvent event, InternalHubSubscriber subscriber) {
			this.event = event;
			this.subscriber = subscriber;
		}

		@Override
		public void run() {
			subscriber.onHubEvent(event);
		}

	}

	public InternalHubProcessor(InternalMonitorHub monitorHub,
			MonitorEndpoint endpoint, Executor executor) {
		this.monitorHub = monitorHub;
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
			event.setContentId(payload.getContentId());
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

	void onStatus(InternalHubPublisher publisher, GatewayStatus status) {
		monitorHub.broadcastStatus(endpoint, endpoint, status);
	}

	void onHubEvent(HubEvent event) {
		for (InternalHubSubscriber subscriber : subscribers) {
			executor.execute(new SendHubEvent(event, subscriber));
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
