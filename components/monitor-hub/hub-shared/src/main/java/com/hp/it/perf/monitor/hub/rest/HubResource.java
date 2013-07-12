package com.hp.it.perf.monitor.hub.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/hub")
public interface HubResource {

	@GET
	@Path("/domain")
	public DomainResource[] domains();

	@GET
	@Path("/domain/{domain}")
	public DomainResource domain(@PathParam("domain") String domain);

	@GET
	@Path("/domain/{domain}/endpoint/{endpointName}")
	public EndpointResource domain(@PathParam("domain") String domain,
			@PathParam("endpointName") String endpointName);

}
