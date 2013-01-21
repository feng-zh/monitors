package com.hp.it.perf.monitor.filemonitor;

import java.io.Serializable;

public class LineRecord implements Serializable {

	private static final long serialVersionUID = 1L;

	private byte[] line;

	private int lineNum;

	private long providerId;

	public byte[] getLine() {
		return line;
	}

	public void setLine(byte[] line) {
		this.line = line;
	}

	public int getLineNum() {
		return lineNum;
	}

	public void setLineNum(int lineNum) {
		this.lineNum = lineNum;
	}

	public long getProviderId() {
		return providerId;
	}

	public void setProviderId(long providerId) {
		this.providerId = providerId;
	}

}
