package com.hp.it.perf.monitor.hub.jmx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

import com.hp.it.perf.monitor.hub.HubEvent;
import com.hp.it.perf.monitor.hub.HubSubscribeOption;
import com.hp.it.perf.monitor.hub.HubSubscriber;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorEvent;
import com.hp.it.perf.monitor.hub.MonitorHub;
import com.hp.it.perf.monitor.hub.support.DefaultHubSubscribeOption;

class JmxHubSubscriber implements NotificationListener {

	private HubSubscriber subscriber;

	private HubSubscribeOption option;

	private Map<MonitorEndpoint, MonitorHubEndpointServiceMXBean> endpoints = new HashMap<MonitorEndpoint, MonitorHubEndpointServiceMXBean>();

	// TODO filter
	private NotificationFilter filter = null;

	private volatile boolean running;

	private MonitorHub monitorHub;

	public JmxHubSubscriber(MonitorHub monitorHub, HubSubscriber subscriber,
			HubSubscribeOption option) {
		this.monitorHub = monitorHub;
		this.subscriber = subscriber;
		this.option = option;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void handleNotification(Notification notification, Object handback) {
		if (!running) {
			return;
		}
		try {
			MonitorEndpoint me = (MonitorEndpoint) handback;
			HubJMX.deserializeNotification(notification);
			notification = HubJMX.decompressNotification(notification);
			List<Notification> list = null;
			if (notification.getUserData() instanceof List) {
				list = (List<Notification>) notification.getUserData();
			} else {
				list = Collections.singletonList((Notification) notification);
			}
			List<MonitorEvent> events = new ArrayList<MonitorEvent>();
			for (Notification newNotification : list) {
				MonitorHubContentData data = (MonitorHubContentData) newNotification
						.getUserData();
				if (MonitorHubEndpointServiceMXBean.NOTIFICATION_MONITOR_EVENT
						.equals(newNotification.getType())) {
					MonitorEvent event = new MonitorEvent(me);
					event.setTime(newNotification.getTimeStamp());
					HubJMX.getMonitorEventContent(event, data);
					events.add(event);
				} else if (MonitorHubEndpointServiceMXBean.NOTIFICATION_HUB_EVENT
						.equals(newNotification.getType())) {
					sendEvents(events);
					HubEvent event = HubJMX
							.getHubEventContent(monitorHub, data);
					try {
						subscriber.onHubEvent(event);
					} catch (Exception e) {
						// TODO
						e.printStackTrace();
					}
				} else {
					sendEvents(events);
					// TODO
					System.err.println("unknown notification "
							+ newNotification);
				}
			}
			sendEvents(events);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendEvents(List<MonitorEvent> events) {
		DefaultHubSubscribeOption.batchOnData(subscriber, option,
				events.toArray(new MonitorEvent[events.size()]));
		events.clear();
	}

	void onHubEvent(HubEvent event) {
		try {
			subscriber.onHubEvent(event);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void startSubscribe() {
		running = true;
	}

	public void addNotificationService(MonitorEndpoint me,
			MonitorHubEndpointServiceMXBean endpointService) {
		((NotificationEmitter) endpointService).addNotificationListener(this,
				filter, me);
		endpoints.put(me, endpointService);
	}

	public void removeNotificationServices() {
		for (Entry<MonitorEndpoint, MonitorHubEndpointServiceMXBean> entry : endpoints
				.entrySet()) {
			try {
				((NotificationEmitter) entry.getValue())
						.removeNotificationListener(this);
			} catch (ListenerNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		endpoints.clear();
	}

	public void stopSubscribe() {
		running = false;
	}

}
