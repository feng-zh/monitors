package com.hp.it.perf.monitor.hub.jmx;

import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXServiceURL;

import com.hp.it.perf.monitor.hub.MonitorHub;

public class MonitorHubJmxFactory {

	public static MonitorHub createHubJmxClient(MBeanServer mbeanServer,
			ObjectName hubObjectName) {
		return new MonitorHubJmxClient(mbeanServer, hubObjectName);
	}

	public static MonitorHub createHubJmxClient(JMXServiceURL jmxServiceURL,
			Map<String, ?> environment, ObjectName hubObjectName) {
		JmxHubConnectorAgent connectorAgent = new JmxHubConnectorAgent(
				jmxServiceURL, environment);
		// TODO provide close function
		MonitorHubJmxClient hubJmxClient = new MonitorHubJmxClient(
				connectorAgent.getMBeanServerConnection(), hubObjectName);
		connectorAgent.addNotificationListener(hubJmxClient, null, null);
		return hubJmxClient;
	}

}
