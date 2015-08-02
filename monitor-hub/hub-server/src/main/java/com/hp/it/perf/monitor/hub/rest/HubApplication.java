package com.hp.it.perf.monitor.hub.rest;

import java.util.Collections;
import java.util.Set;

import javax.ws.rs.core.Application;

import com.hp.it.perf.monitor.hub.MonitorHub;

public class HubApplication extends Application {

	private MonitorHub coreHub;

	public HubApplication(MonitorHub coreHub) {
		this.coreHub = coreHub;
	}

	@Override
	public Set<Object> getSingletons() {
		return Collections.<Object> singleton(new HubResource(coreHub));
	}

}
