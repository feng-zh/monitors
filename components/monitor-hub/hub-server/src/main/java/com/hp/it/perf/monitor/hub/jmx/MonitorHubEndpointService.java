package com.hp.it.perf.monitor.hub.jmx;

import java.util.concurrent.atomic.AtomicLong;

import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;

import com.hp.it.perf.monitor.hub.GatewayPayload;
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

	public MonitorHubEndpointService(MonitorEndpoint endpoint) {
		this.endpoint = endpoint;
	}

	void substribe(MonitorHub coreHub) {
		coreHub.subscribe(this, new DefaultHubSubscribeOption(endpoint));
	}

	void unsubstribe(MonitorHub coreHub) {
		coreHub.unsubscribe(this);
	}

	@Override
	public void onData(MonitorEvent event) {
		Notification notification = new Notification("DATA", this,
				seq.incrementAndGet());
		notification.setTimeStamp(event.getTime());
		MonitorHubContentData data = new MonitorHubContentData();
		data.setContent(event.getContent());
		data.setId(event.getContentId());
		data.setSource(event.getContentSource());
		data.setType(event.getContentType());
		notification.setSource(data);
		sendNotification(notification);
	}

	@Override
	public void onHubEvent(HubEvent event) {
		// TODO Auto-generated method stub

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
	public void publish(GatewayPayload... payloads) {
		// TODO Auto-generated method stub
		
	}
}
