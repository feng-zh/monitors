package com.hp.it.perf.monitor.hub;

import java.util.EventObject;

public class MonitorEvent extends EventObject {

	private long time;

	private Object content;

	private long contentId;

	private int contentType;

	private String contentSource;

	public MonitorEvent(MonitorEndpoint endpoint) {
		super(endpoint);
	}

	public long getTime() {
		return time;
	}

	public Object getContent() {
		return content;
	}

	public long getContentId() {
		return contentId;
	}

	public int getContentType() {
		return contentType;
	}

	public String getContentSource() {
		return contentSource;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public void setContent(Object content) {
		this.content = content;
	}

	public void setContentId(long contentId) {
		this.contentId = contentId;
	}

	public void setContentType(int contentType) {
		this.contentType = contentType;
	}

	public void setContentSource(String contentSource) {
		this.contentSource = contentSource;
	}

}
