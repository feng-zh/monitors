package com.hp.it.perf.monitor.hub;

public class GatewayPayload {

	// private long time;

	private Object content;

	// private long contentId;

	private int contentType;

	private String contentSource;

	public Object getContent() {
		return content;
	}

	public void setContent(Object content) {
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
