package com.hp.it.perf.monitor.hub.rest;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorHub;

@Path("/hub")
public class HubResource {

	final private MonitorHub coreHub;
	private int queueSize;

	HubResource(MonitorHub coreHub, int queueSize) {
		this.coreHub = coreHub;
		this.queueSize = queueSize;
	}

	MonitorHub getHub() {
		return coreHub;
	}

	@GET
	@Path("/domain")
	public DomainResource[] domains() {
		String[] domains = coreHub.getDomains();
		List<DomainResource> domainResources = new ArrayList<DomainResource>();
		for (String domain : domains) {
			domainResources.add(domain0(domain));
		}
		return domainResources.toArray(new DomainResource[domainResources
				.size()]);
	}

	@GET
	@Path("/domain/{domain}")
	public DomainResource domain(@PathParam("domain") String domain) {
		MonitorEndpoint[] endpoints = coreHub.listEndpoints(domain);
		if (endpoints.length == 0) {
			throw new NotFoundException("domain not exist: " + domain);
		} else {
			return domain0(domain);
		}
	}

	private DomainResource domain0(String domain) {
		return new DomainResource(this, domain);
	}

	@GET
	@Path("/domain/{domain}/endpoint/{endpointName}")
	public EndpointResource domain(@PathParam("domain") String domain,
			@PathParam("endpointName") String endpointName) {
		MonitorEndpoint[] endpoints = coreHub.listEndpoints(domain);
		for (MonitorEndpoint endpoint : endpoints) {
			if (endpoint.getName().equals(endpointName)) {
				return new EndpointResource(this, endpoint);
			}
		}
		throw new NotFoundException("endpoint not exit (domain: " + domain
				+ ", name: " + endpointName + ")");
	}

}
