package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class RandomAccessFileReader implements Closeable {

	private RandomAccessFile access;

	private int lineNumber;

	private BytesBuffer lineBuf = new BytesBuffer();

	private int lineBufOffset = 0;

	private byte[] readBuf = new byte[1024];

	private long position;

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
				ensureCapacityInternal(newLen);
			}
			System.arraycopy(data, offset, value, count, len);
			count += len;
			return this;
		}

		private void ensureCapacityInternal(int newLen) {
			// overflow-conscious code
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

		public BytesBuffer insert(int offset, byte[] data) {
			if ((offset < 0) || (offset > length()))
				throw new StringIndexOutOfBoundsException(offset);
			int len = data.length;
			ensureCapacityInternal(count + len);
			System.arraycopy(value, offset, value, offset + len, count - offset);
			System.arraycopy(data, 0, value, offset, len);
			count += len;
			return this;
		}
	}

	public RandomAccessFileReader(File file, long initOffset)
			throws IOException {
		this.access = new RandomAccessFile(file.getAbsoluteFile(), "r");
		if (initOffset < 0) {
			// move to end
			initOffset = Long.MAX_VALUE;
		}
		initOffset = Math.min(initOffset, access.length());
		if (initOffset > 0) {
			access.seek(initOffset);
			this.position = initOffset;
		} else {
			this.position = 0;
		}
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
		position += buf.length;
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
		lineBuf.insert(0, line);
		position -= line.length;
	}

	public long position() {
		return position;
	}
}
