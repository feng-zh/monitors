package com.hp.ts.perf.incubator.filesearch;

import java.io.IOException;
import java.io.OutputStream;

public interface ContentBlock {

	// include, like 0
	public long getStart();

	public int getLength();

	// exclude, like file length
	// = offset + length
	public long getEnd();

	public byte[] toBytes();

	public void writeTo(OutputStream output) throws IOException;

	public int read(byte[] bytes, int offset, int len);

	public int indexOf(byte b);

	public boolean isEmpty();

}
