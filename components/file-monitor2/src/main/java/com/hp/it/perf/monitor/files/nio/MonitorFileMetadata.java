package com.hp.it.perf.monitor.files.nio;

import java.net.URL;

import com.hp.it.perf.monitor.files.FileMetadata;

class MonitorFileMetadata implements FileMetadata {

	private final URL url;
	private final String name;
	private final String path;
	private long lastModifiedDate = -1;
	private long length = 0;

	public MonitorFileMetadata(String name, String path, URL url) {
		this.name = name;
		this.path = path;
		this.url = url;
	}

	public MonitorFileMetadata(MonitorFileMetadata metadata) {
		this.name = metadata.name;
		this.url = metadata.url;
		this.path = metadata.path;
		this.lastModifiedDate = metadata.lastModifiedDate;
		this.length = metadata.length;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isPackaged() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long getLastModifiedDate() {
		return lastModifiedDate;
	}

	void setLastModifiedDate(long date) {
		lastModifiedDate = date;
	}

	@Override
	public String getLength() {
		return String.valueOf(length);
	}

	void setFileLength(long length) {
		this.length = length;
	}

	@Override
	public URL toURL() {
		return url;
	}

	@Override
	public String getPath() {
		return path;
	}

	void invalid() {
		this.lastModifiedDate = 0;
		this.length = -1;
	}

	boolean isMarkUpdated() {
		return this.lastModifiedDate == -1;
	}

	void markUpdated() {
		this.lastModifiedDate = -1;
	}

	boolean isInvalid() {
		return length == -1;
	}

}
