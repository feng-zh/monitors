package com.hp.it.perf.monitor.hub.internal;

import java.util.List;

import com.hp.it.perf.monitor.hub.MonitorEvent;

class SendMonitorEvent implements Runnable {

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
