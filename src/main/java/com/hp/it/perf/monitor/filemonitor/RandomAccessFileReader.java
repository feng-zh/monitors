package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class RandomAccessFileReader implements Closeable {

	private File file;
	private RandomAccessFile access;

	public RandomAccessFileReader(File file) throws FileNotFoundException {
		this.file = file;
		this.access = new RandomAccessFile(file, "r");
	}

	@Override
	public void close() throws IOException {
		access.close();
	}

	public byte[] readLine() {
		// TODO Auto-generated method stub
		return null;
	}

	public int getLineNumber() {
		// TODO Auto-generated method stub
		return 0;
	}

	public void pushBackLine(byte[] line) {
		// TODO Auto-generated method stub

	}

	public long available() {
		// TODO Auto-generated method stub
		return 0;
	}

	public long skip(long len) {
		// TODO Auto-generated method stub
		return 0;
	}

	public long position() {
		// TODO Auto-generated method stub
		return 0;
	}
}
