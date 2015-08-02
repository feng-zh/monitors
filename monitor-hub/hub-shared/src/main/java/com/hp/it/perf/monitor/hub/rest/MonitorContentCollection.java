package com.hp.it.perf.monitor.hub.rest;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlType(name = "MonitorContentCollection")
public class MonitorContentCollection {

	private MonitorContent[] contents;

	private long startId;

	private long nextId;

	private int available;

	private long missing;

	public MonitorContent[] getContents() {
		return contents;
	}

	public void setContents(MonitorContent[] contents) {
		this.contents = contents;
	}

	public long getStartId() {
		return startId;
	}

	public void setStartId(long startId) {
		this.startId = startId;
	}

	public long getNextId() {
		return nextId;
	}

	public void setNextId(long nextId) {
		this.nextId = nextId;
	}

	public int getAvailable() {
		return available;
	}

	public void setAvailable(int available) {
		this.available = available;
	}

	public long getMissing() {
		return missing;
	}

	public void setMissing(long missing) {
		this.missing = missing;
	}

}
