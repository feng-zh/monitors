package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class RandomAccessFileReader implements Closeable {

	private File file;

	private RandomAccessFile access;

	private int lineNumber;

	private BytesBuffer lineBuf = new BytesBuffer();

	private int lineBufOffset = 0;

	private byte[] readBuf = new byte[1024];

	private static class BytesBuffer {

		private byte[] value;

		private int count;

		public BytesBuffer() {
			value = new byte[16];
			count = 0;
		}

		public int indexOf(byte b, int offset) {
			for (int i = offset; i < count; i++) {
				if (b == value[i]) {
					return i;
				}
			}
			return -1;
		}

		public int length() {
			return count;
		}

		public BytesBuffer append(byte[] data, int offset, int len) {
			if (len > 0) {
				int newLen = count + len;
				if (newLen - value.length > 0) {
					int newCapacity = value.length * 2 + 2;
					if (newCapacity - newLen < 0)
						newCapacity = newLen;
					if (newCapacity < 0) {
						if (newLen < 0) // overflow
							throw new OutOfMemoryError();
						newCapacity = Integer.MAX_VALUE;
					}
					value = Arrays.copyOf(value, newCapacity);
				}
			}
			System.arraycopy(data, offset, value, count, len);
			count += len;
			return this;
		}

		public BytesBuffer delete(int start, int end) {
			if (start < 0)
				throw new IndexOutOfBoundsException("index out of range: "
						+ start);
			if (end > count)
				end = count;
			if (start > end)
				throw new IndexOutOfBoundsException();
			int len = end - start;
			if (len > 0) {
				System.arraycopy(value, start + len, value, start, count - end);
				count -= len;
			}
			return this;
		}

		public void getBytes(int fromIndex, int endIndex, byte[] data,
				int offset) {
			if (fromIndex < 0)
				throw new IndexOutOfBoundsException("index out of range: "
						+ fromIndex);
			if ((endIndex < 0) || (endIndex > count))
				throw new IndexOutOfBoundsException("index out of range: "
						+ endIndex);
			if (fromIndex > endIndex)
				throw new StringIndexOutOfBoundsException("srcBegin > srcEnd");
			System.arraycopy(value, fromIndex, data, offset, endIndex
					- fromIndex);
		}
	}

	public RandomAccessFileReader(File file) throws FileNotFoundException {
		this.file = file;
		this.access = new RandomAccessFile(file.getAbsoluteFile(), "r");
	}

	@Override
	public void close() throws IOException {
		access.close();
	}

	// byte[] buf, int off, int len
	private byte[] readLine0() throws IOException {
		// check if new line in it
		int checkOffset = lineBufOffset;
		int newLineOffset;
		while ((newLineOffset = lineBuf.indexOf((byte) '\n', checkOffset)) < 0) {
			// no new line
			checkOffset = lineBuf.length();
			// check underline reader
			int readLen;
			if ((readLen = access.read(readBuf)) != -1) {
				lineBuf.append(readBuf, 0, readLen);
			} else {
				// no data read till now
				// return -1
				return null;
			}
		}
		// get data with new line
		newLineOffset++; // include '\n'
		int rLen = newLineOffset - lineBufOffset;
		byte[] buf = new byte[rLen];
		int off = 0;
		lineBuf.getBytes(lineBufOffset, rLen + lineBufOffset, buf, off);
		lineBufOffset += rLen;
		// if no new line in not-loaded, trim loaded
		if (lineBuf.indexOf((byte) '\n', lineBufOffset) < 0) {
			lineBuf.delete(0, lineBufOffset);
			lineBufOffset = 0;
		}
		return buf;
	}

	public byte[] readLine() throws IOException {
		byte[] line = readLine0();
		if (line != null) {
			lineNumber++;
			return line;
		} else {
			return null;
		}
	}

	public int getLineNumber() {
		return lineNumber;
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
