package com.hp.it.perf.monitor.hub;

import java.io.Serializable;

// domain: hpsc, sbs
// name: env or other special
public class MonitorEndpoint implements Serializable {

	private static final long serialVersionUID = 2707732113864623330L;

	final private String domain;

	final private String name;

	public MonitorEndpoint(String domain, String name) {
		this.domain = domain;
		this.name = name;
	}

	public String getDomain() {
		return domain;
	}

	public String getName() {
		return name;
	}

}
