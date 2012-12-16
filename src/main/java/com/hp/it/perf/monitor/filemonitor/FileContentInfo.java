package com.hp.it.perf.monitor.filemonitor;

import java.io.Serializable;

public class FileContentInfo implements Serializable {

	private static final long serialVersionUID = 1L;

	private String fileName;

	private FileKey fileKey;

	private long providerId;

	private String currentFileName;

	private long offset;

	// real-time information
	private long lastModified;

	private long length;

	public String getFileName() {
		return fileName;
	}

	public long getOffset() {
		return offset;
	}

	public void setOffset(long offset) {
		this.offset = offset;
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

	public FileKey getFileKey() {
		return fileKey;
	}

	public void setFileKey(FileKey fileKey) {
		this.fileKey = fileKey;
	}

	public long getProviderId() {
		return providerId;
	}

	public void setProviderId(long providerId) {
		this.providerId = providerId;
	}

	public String getCurrentFileName() {
		return currentFileName;
	}

	public void setCurrentFileName(String currentFileName) {
		this.currentFileName = currentFileName;
	}

}
