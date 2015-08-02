package com.hp.it.perf.monitor.hub.jmx;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;

import com.hp.it.perf.monitor.hub.HubEvent;
import com.hp.it.perf.monitor.hub.HubSubscriber;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorEvent;
import com.hp.it.perf.monitor.hub.MonitorHub;
import com.hp.it.perf.monitor.hub.support.DefaultHubSubscribeOption;

public class MonitorHubEndpointService extends NotificationBroadcasterSupport
		implements MonitorHubEndpointServiceMXBean, NotificationEmitter,
		HubSubscriber {

	private MonitorEndpoint endpoint;

	private AtomicLong seq = new AtomicLong();

	private boolean notificationCompressEnabled;

	private boolean notificationOpenTypeEnabled;

	private static final int BUFFER_SIZE = Integer.getInteger(
			"notification.buffersize", 1000);

	private static final int BUFFER_TIME = Integer.getInteger(
			"notification.buffertime", 2000);

	private ScheduledExecutorService scheduler;

	private EventBuffer<Notification> eventBuffer;

	public MonitorHubEndpointService(MonitorEndpoint endpoint) {
		this.endpoint = endpoint;
	}

	void substribe(MonitorHub coreHub) {
		startScheduler();
		coreHub.subscribe(this, new DefaultHubSubscribeOption(endpoint));
	}

	private synchronized void startScheduler() {
		if (isNotificationCompressEnabled()) {
			if (scheduler == null) {
				scheduler = Executors
						.newSingleThreadScheduledExecutor(new ThreadFactory() {

							@Override
							public Thread newThread(Runnable r) {
								Thread thread = Executors
										.defaultThreadFactory().newThread(r);
								thread.setDaemon(true);
								thread.setName("EventBuffer Notification Timer - "
										+ endpoint);
								return thread;
							}
						});
			}
		}
	}

	void unsubstribe(MonitorHub coreHub) {
		coreHub.unsubscribe(this);
		stopScheduler();
	}

	private synchronized void stopScheduler() {
		if (scheduler != null) {
			scheduler.shutdown();
			scheduler = null;
		}
	}

	@Override
	public void onData(MonitorEvent... events) {
		for (MonitorEvent event : events) {
			Notification notification = new Notification(
					NOTIFICATION_MONITOR_EVENT, this, 0);
			notification.setTimeStamp(event.getTime());
			MonitorHubContentData data = new MonitorHubContentData();
			HubJMX.setMonitorEventContent(data, event);
			notification.setUserData(data);
			sendNotification(notification);
		}
	}

	@Override
	public void onHubEvent(HubEvent event) {
		Notification notification = new Notification(NOTIFICATION_HUB_EVENT,
				this, 0);
		notification.setTimeStamp(System.currentTimeMillis());
		MonitorHubContentData data = new MonitorHubContentData();
		HubJMX.setHubEventContent(data, event);
		notification.setUserData(data);
		sendNotification(notification);
	}

	public synchronized void sendNotification(Notification notification) {
		if (scheduler != null) {
			if (eventBuffer == null) {
				eventBuffer = new EventBuffer<Notification>(BUFFER_SIZE,
						BUFFER_TIME,
						new EventBuffer.EventBufferHandler<Notification>() {

							@Override
							public void handleBuffer(Queue<Notification> buffer) {
								try {
									Notification notification = new Notification(
											NOTIFICATION_COMPRESSED_EVENT,
											MonitorHubEndpointService.this, seq
													.incrementAndGet(), System
													.currentTimeMillis());
									ArrayList<Notification> list = new ArrayList<Notification>(
											buffer.size());
									Notification data;
									while ((data = buffer.poll()) != null) {
										list.add(data);
									}
									notification.setUserData(list);
									notification = HubJMX
											.compressNotification(notification);
									if (isNotificationOpenTypeEnabled()) {
										HubJMX.serializeNotification(notification);
									}
									MonitorHubEndpointService.super
											.sendNotification(notification);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}, scheduler);
			}
			eventBuffer.add(notification);
		} else {
			if (eventBuffer != null) {
				eventBuffer.flush();
				eventBuffer = null;
			}
			notification.setSequenceNumber(seq.incrementAndGet());
			if (isNotificationOpenTypeEnabled()) {
				HubJMX.serializeNotification(notification);
			}
			super.sendNotification(notification);
		}
	}

	public long getDataCount() {
		return seq.get();
	}

	MonitorEndpoint getEndpoint() {
		return endpoint;
	}

	@Override
	public String getEndpointDomain() {
		return endpoint.getDomain();
	}

	@Override
	public String getEndpointName() {
		return endpoint.getName();
	}

	@Override
	public void setNotificationOpenTypeEnabled(boolean enable) {
		this.notificationOpenTypeEnabled = enable;
	}

	@Override
	public boolean isNotificationOpenTypeEnabled() {
		return notificationOpenTypeEnabled;
	}

	@Override
	public void setNotificationCompressEnabled(boolean enable) {
		this.notificationCompressEnabled = enable;
		if (enable) {
			startScheduler();
		} else {
			stopScheduler();
		}
	}

	@Override
	public boolean isNotificationCompressEnabled() {
		return notificationCompressEnabled;
	}

}
