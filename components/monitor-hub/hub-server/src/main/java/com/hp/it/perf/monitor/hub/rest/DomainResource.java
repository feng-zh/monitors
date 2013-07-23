package com.hp.it.perf.monitor.hub.rest;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.hp.it.perf.monitor.hub.MonitorEndpoint;

public class DomainResource {

	private HubResource hubRes;

	private String domain;

	DomainResource(HubResource hubRes, String domain) {
		this.hubRes = hubRes;
		this.domain = domain;
	}

	@GET
	@Path("/endpoint")
	public EndpointResource[] endpoints() {
		MonitorEndpoint[] endpoints = hubRes.getHub().listEndpoints(domain);
		EndpointResource[] endpointRes = new EndpointResource[endpoints.length];
		for (int i = 0; i < endpoints.length; i++) {
			endpointRes[i] = endpoint0(endpoints[i]);
		}
		return endpointRes;
	}

	private EndpointResource endpoint0(MonitorEndpoint endpoint) {
		return new EndpointResource(hubRes, endpoint);
	}

	@GET
	@Path("/endpoint/{endpointName}")
	public EndpointResource endpoint(
			@PathParam("endpointName") String endpointName) {
		MonitorEndpoint[] endpoints = hubRes.getHub().listEndpoints(domain);
		for (int i = 0; i < endpoints.length; i++) {
			if (endpoints[i].getName().equals(endpointName)) {
				return endpoint0(endpoints[i]);
			}
		}
		throw new NotFoundException("endpoint not exit (domain: " + domain
				+ ", name: " + endpointName + ")");
	}

}
