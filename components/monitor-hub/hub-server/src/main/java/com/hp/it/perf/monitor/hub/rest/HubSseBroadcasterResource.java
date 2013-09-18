package com.hp.it.perf.monitor.hub.rest;

import java.io.IOException;

import javax.inject.Singleton;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.SseBroadcaster;
import org.glassfish.jersey.media.sse.SseFeature;

import com.hp.it.perf.monitor.hub.MonitorHub;

@Singleton
@Path("hub/broadcast")
public class HubSseBroadcasterResource {

	private SseBroadcaster broadcaster = new SseBroadcaster();

	private MonitorHub monitorHub = null;

	static class SubscribeEventOutput extends EventOutput {

		public SubscribeEventOutput(HubSubscribeParam subscribeParam) {
		}

		@Override
		public void write(OutboundEvent chunk) throws IOException {
			// TODO Auto-generated method stub
			super.write(chunk);
		}

	}

	@GET
	@Produces(SseFeature.SERVER_SENT_EVENTS)
	public EventOutput subscribe(@BeanParam HubSubscribeParam subscribeParam) {
		// monitorHub.subscribe(subscriber, option);
		// subscriber
		final EventOutput eventOutput = new SubscribeEventOutput(subscribeParam);
		this.broadcaster.add(eventOutput);
		return eventOutput;
	}

}
