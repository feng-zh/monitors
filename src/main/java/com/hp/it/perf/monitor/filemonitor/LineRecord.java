package com.hp.it.perf.monitor.filemonitor;

import java.io.Serializable;

public class LineRecord implements Serializable {

	private byte[] line;

	private int lineNum;

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

	// private long provideId;

}
