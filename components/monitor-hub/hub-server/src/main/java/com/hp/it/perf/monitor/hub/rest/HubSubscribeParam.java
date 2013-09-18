package com.hp.it.perf.monitor.hub.rest;

import java.util.List;

import javax.ws.rs.QueryParam;

public class HubSubscribeParam {

	@QueryParam("endpoint")
	private List<String> endpoints;

	public List<String> getEndpoints() {
		return endpoints;
	}

}
