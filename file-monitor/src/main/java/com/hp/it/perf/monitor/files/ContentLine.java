package com.hp.it.perf.monitor.files;

public class ContentLine {

	private long position = -1; // unknown

	private byte[] line;

	private transient FileInstance fileInstance;

	public long getPosition() {
		return position;
	}

	public void setPosition(long position) {
		this.position = position;
	}

	public byte[] getLine() {
		return line;
	}

	public void setLine(byte[] line) {
		this.line = line;
	}

	public FileInstance getFileInstance() {
		return fileInstance;
	}

	public void setFileInstance(FileInstance fileInstance) {
		this.fileInstance = fileInstance;
	}

	public String toString() {
		return (fileInstance != null ? fileInstance.getName() : "UNKNOWN")
				+ "@" + getPosition() + ":" + new String(getLine());
	}

}
