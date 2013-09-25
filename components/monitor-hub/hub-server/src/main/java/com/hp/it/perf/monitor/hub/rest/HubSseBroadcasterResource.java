package com.hp.it.perf.monitor.hub.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.SseFeature;

import com.hp.it.perf.monitor.hub.HubEvent;
import com.hp.it.perf.monitor.hub.HubSubscriber;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorEvent;
import com.hp.it.perf.monitor.hub.MonitorHub;

public class HubSseBroadcasterResource implements HubSubscriber {

	private MonitorHub coreHub;
	private volatile EventOutput eventOutput;

	HubSseBroadcasterResource(MonitorHub coreHub) {
		this.coreHub = coreHub;
		System.out.println("CREATE broadcaster resource");
	}

	@GET
	@Produces(SseFeature.SERVER_SENT_EVENTS)
	public EventOutput subscribe() {
		eventOutput = new EventOutput();
		return eventOutput;
	}

	@Override
	public void onData(MonitorEvent... events) {
		for (MonitorEvent event : events) {
			if (eventOutput != null) {
				MonitorContent content = new MonitorContent();
				content.setContentId(event.getContentId());
				content.setContentSource(event.getContentSource());
				content.setContentType(event.getContentType());
				content.setTime(event.getTime());
				content.setContent(event.getContent());
				content.setEndpoint(((MonitorEndpoint) event.getSource())
						.toString());
				OutboundEvent outboundEvent = new OutboundEvent.Builder()
						.mediaType(MediaType.APPLICATION_JSON_TYPE)
						.name("data").data(MonitorContent.class, content)
						.build();
				sendEvent(outboundEvent);
			}
		}
	}

	@Override
	public void onHubEvent(HubEvent event) {
		if (eventOutput != null) {
			HubContent content = new HubContent();
			content.setStatus(event.getStatus().ordinal());
			content.setEndpoint(event.getEndpoint() == null ? "" : event
					.getEndpoint().toString());
			content.setContent(event.getData());
			OutboundEvent outboundEvent = new OutboundEvent.Builder()
					.mediaType(MediaType.APPLICATION_JSON_TYPE).name("hub")
					.data(HubContent.class, content).build();
			sendEvent(outboundEvent);
		}
	}

	private void sendEvent(OutboundEvent event) {
		EventOutput output = eventOutput;
		if (output != null) {
			if (!output.isClosed()) {
				try {
					output.write(event);
				} catch (Exception e) {
					fireOnException(e);
				}
			}
			if (output.isClosed()) {
				if (eventOutput != null) {
					eventOutput = null;
					fireOnClose();
				}
			}
		}
	}

	private void fireOnException(Exception e) {
		// e.printStackTrace();
	}

	private void fireOnClose() {
		System.out.println("unsubscribe");
		coreHub.unsubscribe(this);
	}

}
