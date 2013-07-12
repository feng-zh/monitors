package com.hp.it.perf.monitor.hub.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

public interface DomainResource {

	@GET
	@Path("/endpoint")
	public EndpointResource[] endpoints();

	@GET
	@Path("/endpoint/{endpointName}")
	public EndpointResource endpoint(
			@PathParam("endpointName") String endpointName);

}
