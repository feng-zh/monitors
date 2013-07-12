package com.hp.it.perf.monitor.hub.jmx;

import java.io.Serializable;

public class MonitorHubContentData implements Serializable {

	private static final long serialVersionUID = 6996526155736191309L;

	private Object content;

	private long id;

	private int type;

	private String source;

	public Object getContent() {
		return content;
	}

	public void setContent(Object content) {
		this.content = content;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

}
