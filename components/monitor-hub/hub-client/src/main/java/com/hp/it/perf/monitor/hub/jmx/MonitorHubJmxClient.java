package com.hp.it.perf.monitor.hub.jmx;

import java.util.ArrayList;
import java.util.List;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import com.hp.it.perf.monitor.hub.HubSubscribeOption;
import com.hp.it.perf.monitor.hub.HubSubscriber;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorEvent;
import com.hp.it.perf.monitor.hub.MonitorHub;

public class MonitorHubJmxClient implements MonitorHub {

	private MonitorHubServiceMXBean mbean;

	private MBeanServerConnection mbeanServer;

	private ObjectName hubObjectName;

	public MonitorHubJmxClient(MBeanServerConnection mbeanServer,
			ObjectName hubObjectName) {
		this.mbeanServer = mbeanServer;
		this.hubObjectName = hubObjectName;
		this.mbean = JMX.newMXBeanProxy(mbeanServer, hubObjectName,
				MonitorHubServiceMXBean.class, true);
	}

	@Override
	public MonitorEndpoint[] listEndpoints(String domainFilter) {
		return mbean.listEndpoints(domainFilter);
	}

	@Override
	public void subscribe(final HubSubscriber subscriber,
			HubSubscribeOption option) {
		MonitorEndpoint[] endpoints = option.getPreferedEndpoints();
		if (endpoints.length == 0) {
			List<MonitorEndpoint> list = new ArrayList<MonitorEndpoint>();
			MonitorEndpoint[] all = listEndpoints(null);
			for (MonitorEndpoint me : all) {
				if (option.isSubscribeEnabled(me)) {
					list.add(me);
				}
			}
			endpoints = list.toArray(new MonitorEndpoint[list.size()]);
		}
		if (endpoints.length == 0) {
			// nothing to subscribe
			// TODO dynamic added
			// TODO unsubstribe
			return;
		}
		NotificationListener delegate = new NotificationListener() {

			@Override
			public void handleNotification(Notification notification,
					Object handback) {
				MonitorEndpoint me = (MonitorEndpoint) handback;
				MonitorHubContentData data = (MonitorHubContentData) notification
						.getUserData();
				long time = notification.getTimeStamp();
				MonitorEvent event = new MonitorEvent(me);
				event.setContent(data.getContent());
				event.setContentId(data.getId());
				event.setTime(time);
				event.setContentSource(data.getSource());
				event.setContentType(data.getType());
				subscriber.onData(event);
			}
		};
		for (MonitorEndpoint me : endpoints) {
			MonitorHubEndpointServiceMXBean endpointService = JMX
					.newMXBeanProxy(mbeanServer,
							HubJMX.createEndpointObjectName(hubObjectName, me),
							MonitorHubEndpointServiceMXBean.class, true);
			NotificationFilter filter = null;
			// TODO filter
			((NotificationEmitter) endpointService).addNotificationListener(
					delegate, filter, me);
		}
	}

	@Override
	public void unsubscribe(HubSubscriber subscriber) {
		// TODO Auto-generated method stub

	}

}
