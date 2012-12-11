package com.hp.it.perf.monitor.filemonitor;

import java.io.Serializable;

public class FileContentInfo implements Serializable {

	private String fileContentKey;

	private String fileName;
	
	private long lastModified;

	private long length;

	private Object fileKey;

	public String getFileContentKey() {
		return fileContentKey;
	}

	public void setFileContentKey(String fileContentKey) {
		this.fileContentKey = fileContentKey;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public long getLastModified() {
		return lastModified;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	public long getLength() {
		return length;
	}

	public void setLength(long length) {
		this.length = length;
	}

	public Object getFileKey() {
		return fileKey;
	}

	public void setFileKey(Object fileKey) {
		this.fileKey = fileKey;
	}

}
