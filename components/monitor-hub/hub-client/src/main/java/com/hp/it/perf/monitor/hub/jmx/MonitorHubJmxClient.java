package com.hp.it.perf.monitor.hub.jmx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectionNotification;

import com.hp.it.perf.monitor.hub.HubEvent;
import com.hp.it.perf.monitor.hub.HubEvent.HubStatus;
import com.hp.it.perf.monitor.hub.HubPublishOption;
import com.hp.it.perf.monitor.hub.HubPublisher;
import com.hp.it.perf.monitor.hub.HubSubscribeOption;
import com.hp.it.perf.monitor.hub.HubSubscriber;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorHub;

class MonitorHubJmxClient implements MonitorHub, NotificationListener {

	private MonitorHubServiceMXBean mbean;

	private MBeanServerConnection mbeanServer;

	private ObjectName hubObjectName;

	private Map<HubSubscriber, JmxHubSubscriber> subscribers = new ConcurrentHashMap<HubSubscriber, JmxHubSubscriber>();

	public MonitorHubJmxClient(MBeanServerConnection mbeanServer,
			ObjectName hubObjectName) {
		this.mbeanServer = mbeanServer;
		this.hubObjectName = hubObjectName;
		this.mbean = JMX.newMXBeanProxy(this.mbeanServer, hubObjectName,
				MonitorHubServiceMXBean.class, true);
	}

	@Override
	public MonitorEndpoint[] listEndpoints(String domainFilter) {
		return mbean.listEndpoints(domainFilter);
	}

	@Override
	public void subscribe(final HubSubscriber subscriber,
			HubSubscribeOption option) {
		if (!subscribers.containsKey(subscriber)) {
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
			JmxHubSubscriber jmxSubscriber = new JmxHubSubscriber(this,
					subscriber, option);
			for (MonitorEndpoint me : endpoints) {
				MonitorHubEndpointServiceMXBean endpointService = JMX
						.newMXBeanProxy(mbeanServer, HubJMX
								.createEndpointObjectName(hubObjectName, me),
								MonitorHubEndpointServiceMXBean.class, true);
				jmxSubscriber.addNotificationService(me, endpointService);
			}
			this.subscribers.put(subscriber, jmxSubscriber);
			jmxSubscriber.startSubscribe();
		}
	}

	@Override
	public void unsubscribe(HubSubscriber subscriber) {
		JmxHubSubscriber jmxSubscriber = subscribers.remove(subscriber);
		if (jmxSubscriber != null) {
			jmxSubscriber.stopSubscribe();
			jmxSubscriber.removeNotificationServices();
		}
	}

	@Override
	public String[] getDomains() {
		return mbean.getDomains();
	}

	@Override
	public HubPublisher createPublisher(MonitorEndpoint endpoint,
			HubPublishOption option) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException(
				"not implemented in this version");
	}

	private void sendHubEvent(HubEvent event) {
		for (JmxHubSubscriber subscriber : subscribers.values()) {
			subscriber.onHubEvent(event);
		}
	}

	@Override
	public void handleNotification(Notification notification, Object handback) {
		if (notification instanceof JMXConnectionNotification) {
			JMXConnectionNotification jmxConnNotification = (JMXConnectionNotification) notification;
			if (JMXConnectionNotification.NOTIFS_LOST
					.equals(jmxConnNotification.getType())) {
				HubEvent event = new HubEvent(this, HubStatus.DataLost, null,
						(Long) jmxConnNotification.getUserData());
				sendHubEvent(event);
			} else if (JMXConnectionNotification.CLOSED
					.equals(jmxConnNotification.getType())) {
				HubEvent event = new HubEvent(this, HubStatus.Disconnected,
						null, jmxConnNotification.getMessage());
				sendHubEvent(event);
			} else if (JMXConnectionNotification.OPENED
					.equals(jmxConnNotification.getType())) {
				HubEvent event = new HubEvent(this, HubStatus.Connected, null,
						jmxConnNotification.getMessage());
				sendHubEvent(event);
			} else if (JMXConnectionNotification.FAILED
					.equals(jmxConnNotification.getType())) {
				// TODO
				System.out.println(getClass().getName() + "- failed: "
						+ jmxConnNotification.getMessage());
			}
		}
	}

}
