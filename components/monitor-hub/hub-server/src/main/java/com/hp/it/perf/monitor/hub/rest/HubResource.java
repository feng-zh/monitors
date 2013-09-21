package com.hp.it.perf.monitor.hub.rest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorHub;
import com.hp.it.perf.monitor.hub.support.DefaultHubSubscribeOption;

@Path("/hub")
public class HubResource {

	final private MonitorHub coreHub;

	HubResource(MonitorHub coreHub) {
		this.coreHub = coreHub;
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

	@Path("/broadcast")
	public HubSseBroadcasterResource broadcast(
			@BeanParam HubSubscribeParam subscribeParam) {
		HubSseBroadcasterResource broadcasterResource = new HubSseBroadcasterResource(
				coreHub);
		DefaultHubSubscribeOption option;
		if (subscribeParam.getEndpoints().isEmpty()) {
			// all listened
			option = null;
		} else {
			Set<MonitorEndpoint> endpoints = new HashSet<MonitorEndpoint>();
			for (String endpointSpec : subscribeParam.getEndpoints()) {
				try {
					endpoints.add(MonitorEndpoint.valueOf(endpointSpec));
				} catch (IllegalArgumentException e) {
					throw new BadRequestException("invalid endpoint spec: "
							+ endpointSpec);
				}
			}
			option = new DefaultHubSubscribeOption(
					endpoints.toArray(new MonitorEndpoint[endpoints.size()]));
		}
		coreHub.subscribe(broadcasterResource, option);
		return broadcasterResource;
	}
}
