package com.hp.ts.perf.filesearch.impl;

import java.io.IOException;
import java.io.OutputStream;

import com.hp.ts.perf.filesearch.ContentBlock;

class BytesContentBlock implements ContentBlock {

	private long start;

	private byte[] data;

	private int length;

	public BytesContentBlock(long start, byte[] data, int length) {
		if (start < 0) {
			throw new IllegalArgumentException("start is less than 0");
		}
		if (length < 0) {
			throw new IllegalArgumentException("length is less than 0");
		}
		this.start = start;
		this.data = data;
		this.length = length;
	}

	@Override
	public long getStart() {
		return start;
	}

	@Override
	public int getLength() {
		return length;
	}

	@Override
	public long getEnd() {
		return getStart() + getLength();
	}

	@Override
	public byte[] toBytes() {
		if (data.length != length) {
			byte[] ret = new byte[length];
			System.arraycopy(data, 0, ret, 0, length);
			data = ret;
		}
		return data;
	}

	@Override
	public void writeTo(OutputStream output) throws IOException {
		output.write(data, 0, length);
	}

	public String toString() {
		return new String(toBytes());
	}

	@Override
	public int read(byte[] bytes, int offset, int len) {
		int size = Math.min(len, length);
		System.arraycopy(data, 0, bytes, offset, size);
		return size;
	}

	@Override
	public int indexOf(byte b) {
		for (int i = 0, n = data.length; i < n; i++) {
			if (data[i] == b) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean isEmpty() {
		return getLength() == 0;
	}

}
