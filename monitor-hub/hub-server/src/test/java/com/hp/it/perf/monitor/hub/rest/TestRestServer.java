package com.hp.it.perf.monitor.hub.rest;

import java.io.Closeable;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.ext.RuntimeDelegate;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainer;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.sse.SseFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.hp.it.perf.monitor.hub.GatewayPayload;
import com.hp.it.perf.monitor.hub.HubPublisher;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.internal.InternalMonitorHub;

public class TestRestServer {

	static {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		InternalMonitorHub coreHub = new InternalMonitorHub();
		HubPublisher publisher = coreHub.createPublisher(new MonitorEndpoint(
				"test", "test"), null);
		try {
			final HttpServer server = HttpServer.createSimpleServer("/", 17008);
			ResourceConfig resourceConfig = ResourceConfig
					.forApplication(new HubApplication(coreHub));
			resourceConfig.register(SseFeature.class);
			resourceConfig.register(JacksonFeature.class);
			HttpHandler handler = RuntimeDelegate.getInstance().createEndpoint(
					resourceConfig, GrizzlyHttpContainer.class);
			server.getServerConfiguration().addHttpHandler(handler, "/myhub");
			server.start();
			while (true) {
				GatewayPayload payload = new GatewayPayload();
				payload.setContent(new Date().toString());
				payload.setContentSource("source");
				// TODO how to use this content type in hub
				payload.setContentType(1);
				publisher.post(payload);
				TimeUnit.SECONDS.sleep(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(publisher);
		}
	}

	private static void close(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				// TODO
				e.printStackTrace();
			}
		}
	}

}
