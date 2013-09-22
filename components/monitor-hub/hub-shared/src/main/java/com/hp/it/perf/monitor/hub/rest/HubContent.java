package com.hp.it.perf.monitor.hub.rest;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "HubContent")
public class HubContent {

	private String endpoint;

	private Object content;

	private int status;

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public Object getContent() {
		return content;
	}

	public void setContent(Object content) {
		this.content = content;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

}
