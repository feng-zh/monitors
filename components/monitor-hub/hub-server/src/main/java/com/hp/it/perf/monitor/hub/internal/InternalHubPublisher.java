package com.hp.it.perf.monitor.hub.internal;

import com.hp.it.perf.monitor.hub.GatewayPayload;
import com.hp.it.perf.monitor.hub.HubPublishOption;
import com.hp.it.perf.monitor.hub.HubPublisher;

class InternalHubPublisher implements HubPublisher {

	private InternalHubProcessor processor;

	private HubPublishOption option;

	private volatile boolean closed = false;

	InternalHubPublisher(InternalHubProcessor processor, HubPublishOption option) {
		this.processor = processor;
		this.option = option;
	}

	@Override
	public void post(GatewayPayload... payloads) {
		if (closed) {
			throw new IllegalStateException("closed publisher");
		}
		if (payloads.length == 0) {
			return;
		}
		if (option != null && option.getPayloadConverter() != null) {
			// TODO
		}
		processor.onData(this, payloads);
	}

	@Override
	public void close() {
		if (!closed) {
			closed = true;
			processor.removePublisher(this);
		}
	}

}
