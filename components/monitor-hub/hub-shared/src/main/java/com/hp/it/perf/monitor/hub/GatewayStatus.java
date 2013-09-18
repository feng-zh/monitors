package com.hp.it.perf.monitor.hub;

import java.io.Serializable;

public class GatewayStatus implements Serializable {

	private static final long serialVersionUID = 8423963706327746130L;

	private int status;

	private Object context;

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public Object getContext() {
		return context;
	}

	public void setContext(Object context) {
		this.context = context;
	}

	@Override
	public String toString() {
		return String.format("GatewayStatus [status=%s, context=%s]", status,
				context);
	}

}
