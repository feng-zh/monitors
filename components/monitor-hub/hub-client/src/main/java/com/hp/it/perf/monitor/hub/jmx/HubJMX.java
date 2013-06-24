package com.hp.it.perf.monitor.hub.jmx;

import java.util.Hashtable;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import com.hp.it.perf.monitor.hub.MonitorEndpoint;

public class HubJMX {

	public static ObjectName createEndpointObjectName(ObjectName hubObjectName,
			MonitorEndpoint me) {
		Hashtable<String, String> prop = new Hashtable<String, String>();
		prop.put("domain", me.getDomain());
		prop.put("name", me.getName());
		ObjectName endpointName;
		try {
			endpointName = ObjectName.getInstance(hubObjectName.getDomain(),
					prop);
		} catch (MalformedObjectNameException e) {
			throw new IllegalArgumentException("invalid endpoint name: " + me,
					e);
		}
		return endpointName;
	}
}
