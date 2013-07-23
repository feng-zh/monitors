package com.hp.it.perf.monitor.hub.rest;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorHub;

public class EndpointResource {

	private HubResource hubRes;
	private MonitorEndpoint endpoint;

	EndpointResource(HubResource hubRes, MonitorEndpoint endpoint) {
		this.hubRes = hubRes;
		this.endpoint = endpoint;
	}

	@GET
	@Path("/contents")
	public MonitorContentCollection contents(
			@QueryParam("limit") @DefaultValue("0") int limit,
			@QueryParam("marker") @DefaultValue("0") long marker,
			@QueryParam("timeout") @DefaultValue("0") int timeout) {
		// TODO
		return null;
	}

	@GET
	@Path("/contents/{contentId}")
	public MonitorContent content(@PathParam("contentId") long contentId) {
		// TODO
		return null;
	}

	// statistics
	// control/manage
}
