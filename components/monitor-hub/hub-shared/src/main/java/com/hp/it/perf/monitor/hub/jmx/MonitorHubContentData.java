package com.hp.it.perf.monitor.hub.jmx;

import java.io.Serializable;

public class MonitorHubContentData implements Serializable {

	private static final long serialVersionUID = -7340156872106166623L;

	private byte[] content;

	private long id;

	private int type;

	private String source;

	// used for internal JMX data exchange
	// 0b0xxxxxx (raw event) >=0
	// 0b1xxxxxx (compressed event)
	private byte dataType;

	public byte[] getContent() {
		return content;
	}

	public void setContent(byte[] content) {
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

	public byte getDataType() {
		return dataType;
	}

	public void setDataType(byte dataType) {
		this.dataType = dataType;
	}

}
