package com.hp.it.perf.monitor.hub;

import java.io.Serializable;


public interface MonitorFilter extends Serializable {

	public boolean accept(MonitorEndpoint endpoint, MonitorEvent event);

}
