package com.hp.it.perf.monitor.hub;

import java.io.Serializable;

// domain: hpsc, sbs
// name: env or other special
public class MonitorEndpoint implements Serializable {

	private static final long serialVersionUID = 2707732113864623330L;

	final private String domain;

	final private String name;

	public MonitorEndpoint(String domain, String name) {
		if (domain.indexOf(':') != -1) {
			throw new IllegalArgumentException("invalid domain");
		}
		this.domain = domain;
		this.name = name;
	}

	public String getDomain() {
		return domain;
	}

	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((domain == null) ? 0 : domain.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof MonitorEndpoint))
			return false;
		MonitorEndpoint other = (MonitorEndpoint) obj;
		if (domain == null) {
			if (other.domain != null)
				return false;
		} else if (!domain.equals(other.domain))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return domain + ":" + name;
	}

	public static MonitorEndpoint valueOf(String value) {
		int index = value.indexOf(':');
		if (index != -1) {
			return new MonitorEndpoint(value.substring(0, index),
					value.substring(index + 1));
		} else {
			throw new IllegalArgumentException("invalid endpoint: " + value);
		}
	}

}
