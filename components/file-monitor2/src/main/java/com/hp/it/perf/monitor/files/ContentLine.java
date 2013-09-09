package com.hp.it.perf.monitor.files;

public class ContentLine {

	private byte[] line;

	private transient FileInstance fileInstance;

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

}
