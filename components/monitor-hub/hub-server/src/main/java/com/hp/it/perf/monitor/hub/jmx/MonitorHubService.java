package com.hp.it.perf.monitor.hub.jmx;

import java.util.ArrayList;
import java.util.List;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;

import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorHub;

public class MonitorHubService extends NotificationBroadcasterSupport implements
		MonitorHubServiceMXBean, MBeanRegistration {

	private MonitorHub coreHub;

	private MBeanServer mbeanServer;

	private ObjectName objectName;

	private List<MonitorHubEndpointService> endpointsService = new ArrayList<MonitorHubEndpointService>();

	private boolean notificationCompressDefault;

	private boolean notificationOpenTypeDefault;

	public MonitorHubService(MonitorHub coreHub) {
		this.coreHub = coreHub;
	}

	@Override
	public MonitorEndpoint[] listEndpoints(String domainFilter) {
		return coreHub.listEndpoints(domainFilter);
	}

	@Override
	public ObjectName preRegister(MBeanServer server, ObjectName name)
			throws Exception {
		this.mbeanServer = server;
		this.objectName = name;
		registerEndpoints(name);
		return name;
	}

	private void registerEndpoints(ObjectName hubName) throws Exception {
		for (MonitorEndpoint me : listEndpoints(null)) {
			MonitorHubEndpointService endpointService = new MonitorHubEndpointService(
					me);
			endpointService
					.setNotificationOpenTypeEnabled(notificationOpenTypeDefault);
			endpointService
					.setNotificationCompressEnabled(notificationCompressDefault);
			ObjectName endpointName = HubJMX.createEndpointObjectName(hubName,
					me);
			mbeanServer.registerMBean(endpointService, endpointName);
			endpointService.substribe(coreHub);
			endpointsService.add(endpointService);
		}
	}

	@Override
	public void postRegister(Boolean registrationDone) {
		if (Boolean.FALSE.equals(registrationDone)) {
			deregister();
		}
	}

	private void deregister() {
		for (int i = endpointsService.size(); i >= 0; i--) {
			MonitorHubEndpointService mes = endpointsService.remove(i);
			ObjectName endpointName;
			try {
				mes.unsubstribe(coreHub);
				endpointName = HubJMX.createEndpointObjectName(objectName,
						mes.getEndpoint());
				mbeanServer.unregisterMBean(endpointName);
			} catch (MBeanRegistrationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InstanceNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void preDeregister() throws Exception {
		deregister();
	}

	@Override
	public void postDeregister() {
		this.mbeanServer = null;
		this.objectName = null;
	}

	@Override
	public String[] getDomains() {
		return coreHub.getDomains();
	}

	@Override
	public void setNotificationOpenTypeDefault(boolean enable) {
		this.notificationOpenTypeDefault = enable;
	}

	@Override
	public boolean isNotificationOpenTypeDefault() {
		return notificationOpenTypeDefault;
	}

	@Override
	public void setNotificationCompressDefault(boolean enable) {
		this.notificationCompressDefault = enable;
	}

	@Override
	public boolean isNotificationCompressDefault() {
		return notificationCompressDefault;
	}

}
