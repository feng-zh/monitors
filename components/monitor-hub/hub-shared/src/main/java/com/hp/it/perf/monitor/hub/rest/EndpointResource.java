package com.hp.it.perf.monitor.hub.rest;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

public interface EndpointResource {

	@GET
	@Path("/contents")
	public MonitorContentCollection contents(
			@QueryParam("limit") @DefaultValue("0") int limit,
			@QueryParam("marker") @DefaultValue("0") long marker,
			@QueryParam("timeout") @DefaultValue("0") int timeout);

	@GET
	@Path("/contents/{contentId}")
	public MonitorContent content(@PathParam("contentId") long contentId);

	// statistics
	// control/manage
}
