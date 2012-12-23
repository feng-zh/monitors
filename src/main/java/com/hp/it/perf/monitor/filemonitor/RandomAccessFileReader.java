package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomAccessFileReader implements Closeable {

	private static Logger log = LoggerFactory
			.getLogger(RandomAccessFileReader.class);

	private RandomAccessFile access;

	private int loadedLineNumber;

	private BytesBuffer lineBuf = new BytesBuffer();

	private int lineBufOffset = 0;

	private byte[] readBuf = new byte[8 * 1024];

	private long position;

	private File file;

	private int idleTimeout = 0;

	private boolean closed = true;

	private static DelayQueue<TimeoutReaderReference> keepAliveQueue = new DelayQueue<TimeoutReaderReference>();

	private TimeoutReaderReference timeoutReference;

	static {
		Thread keepAliveThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					try {
						RandomAccessFileReader timeoutReader = keepAliveQueue
								.take().getReader();
						if (timeoutReader != null) {
							timeoutReader.tryOffline();
						}
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		});
		keepAliveThread.setDaemon(true);
		keepAliveThread.setName("Reader keepalive thread");
		keepAliveThread.start();
	}

	private static class TimeoutReaderReference implements Delayed {

		private final WeakReference<RandomAccessFileReader> readerRef;
		private final int timeout;
		private volatile long delayTime;
		private volatile long lastCheckNanoTime;

		public TimeoutReaderReference(RandomAccessFileReader reader,
				int timeoutSec) {
			this.readerRef = new WeakReference<RandomAccessFileReader>(reader);
			this.timeout = timeoutSec;
			ping();
		}

		public void ping() {
			lastCheckNanoTime = System.nanoTime();
			delayTime = TimeUnit.SECONDS.toNanos(timeout);
		}

		@Override
		public int compareTo(Delayed o) {
			long od = o.getDelay(TimeUnit.NANOSECONDS);
			long md = getDelay(TimeUnit.NANOSECONDS);
			if (md < od) {
				return -1;
			} else if (md > od) {
				return 1;
			} else {
				return 0;
			}
		}

		@Override
		public long getDelay(TimeUnit unit) {
			long current = System.nanoTime();
			delayTime -= (current - lastCheckNanoTime);
			lastCheckNanoTime = current;
			return unit.convert(delayTime, TimeUnit.NANOSECONDS);
		}

		public RandomAccessFileReader getReader() {
			return readerRef.get();
		}

	}

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

	public RandomAccessFileReader(File file) {
		this.file = file;
	}

	private synchronized void tryOffline() {
		if (timeoutReference.getDelay(TimeUnit.NANOSECONDS) > 0) {
			// maybe just keep alive again
			keepAliveQueue.add(timeoutReference);
		} else {
			timeoutReference = null;
			// start off-line
			// off access reader
			try {
				close0();
			} catch (IOException e) {
				log.warn("close access file got error: {}", e.toString());
			}
		}

	}

	private synchronized void tryKeepAlive() {
		if (idleTimeout > 0) {
			if (timeoutReference == null) {
				timeoutReference = new TimeoutReaderReference(this, idleTimeout);
				keepAliveQueue.add(timeoutReference);
			} else {
				timeoutReference.ping();
			}
		}
	}

	public void setKeepAlive(int idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

	public void open(long initOffset, boolean lazyOpen) throws IOException {
		close();
		this.closed = false;
		long len = file.length();
		this.position = initOffset < 0 ? len : Math.min(initOffset, len);
		if (!lazyOpen) {
			open0();
		}
	}

	private void open0() throws FileNotFoundException, IOException {
		this.access = new RandomAccessFile(file, "r");
		access.seek(position);
		log.debug("open random access file {} at offset {}", file, position);
	}

	@Override
	public void close() throws IOException {
		closed = true;
		keepAliveQueue.remove(timeoutReference);
		timeoutReference = null;
		close0();
	}

	private void close0() throws IOException {
		RandomAccessFile accessFile = access;
		access = null;
		if (accessFile != null) {
			// reset buffer
			lineBufOffset = 0;
			lineBuf.delete(0, lineBuf.count);
			// close access
			accessFile.close();
			log.debug("close random access file {} at offset {}", file,
					position);
		}
	}

	// byte[] buf, int off, int len
	private byte[] readLine0() throws IOException {
		log.trace("readline for {}", file);
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
		// check if it is open
		if (closed) {
			throw new IOException("file is not open or closed");
		}
		if (access == null) {
			// lazy open
			open0();
		}
		byte[] line = readLine0();
		log.trace("readline got {} bytes",
				(line == null ? "0" : Integer.toString(line.length)));
		if (line != null) {
			loadedLineNumber++;
			return line;
		} else {
			// no data loaded, start keepAlive
			tryKeepAlive();
			return null;
		}
	}

	public int getLoadedLineNumber() {
		return loadedLineNumber;
	}

	void pushBackLine(byte[] line) {
		lineBuf.insert(0, line);
		position -= line.length;
	}

	public long position() {
		return position;
	}
}
