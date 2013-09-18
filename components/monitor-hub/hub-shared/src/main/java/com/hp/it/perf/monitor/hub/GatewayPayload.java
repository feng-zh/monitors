package com.hp.it.perf.monitor.hub;

import java.io.Serializable;

public class GatewayPayload implements Serializable {

	private static final long serialVersionUID = -5688054799154644888L;

	// private long time;

	private byte[] content;

	// private long contentId;

	private int contentType;

	private String contentSource;

	public byte[] getContent() {
		return content;
	}

	public void setContent(byte[] content) {
		this.content = content;
	}

	public int getContentType() {
		return contentType;
	}

	public void setContentType(int contentType) {
		this.contentType = contentType;
	}

	public String getContentSource() {
		return contentSource;
	}

	public void setContentSource(String contentSource) {
		this.contentSource = contentSource;
	}

}
