package com.hp.it.perf.monitor.files.nio;

import java.io.EOFException;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.ContentLine;
import com.hp.it.perf.monitor.files.ContentLineSourceObserver;
import com.hp.it.perf.monitor.files.ContentLineStream;
import com.hp.it.perf.monitor.files.FileContentChangeListener;
import com.hp.it.perf.monitor.files.FileInstance;
import com.hp.it.perf.monitor.files.FileInstanceChangeListener;

class MonitorFileStream implements ContentLineStream,
		FileContentChangeListener, FileInstanceChangeListener {

	private RandomAccessFileReader reader;

	private volatile boolean closed;

	private MonitorFileInstance fileInstance;

	private Lock lock = new ReentrantLock();

	private Condition noChanged = lock.newCondition();

	private volatile boolean stopRead;

	private final static Object OffsetTracker = new Object();

	private boolean monitorable;

	private ContentLineSourceObserver sourceObserver;

	// default 600 seconds
	private int idleTimeout = Integer.getInteger("monitor.reader.idleTimeout", 600);

	private static final Logger log = LoggerFactory
			.getLogger(MonitorFileStream.class);

	public MonitorFileStream(MonitorFileInstance fileInstance, long initOffset,
			boolean lazyOpen, boolean monitorable) throws IOException {
		this.fileInstance = fileInstance;
		this.monitorable = monitorable;
		reader = new RandomAccessFileReader(fileInstance.getFile());
		fileInstance.getStatistics().ioReaderCount().increment();
		reader.setStatisticis(fileInstance.getStatistics());
		reader.open(initOffset, lazyOpen);
		reader.setKeepAlive(idleTimeout);
		fileInstance.addFileInstanceChangeListener(this);
		if (monitorable) {
			fileInstance.addFileContentChangeListener(this);
		}
	}

	@Override
	public ContentLine poll() throws IOException {
		checkClosed();
		byte[] line = readLine();
		if (line == null) {
			return null;
		} else {
			ContentLine content = new ContentLine();
			content.setFileInstance(fileInstance);
			content.setLine(line);
			return content;
		}
	}

	@Override
	public ContentLine take() throws IOException, InterruptedException {
		while (true) {
			checkClosed();
			byte[] line = readLine();
			if (line == null) {
				if (monitorable && !stopRead) {
					lock.lock();
					log.trace("wait for file updated from {}", fileInstance);
					try {
						noChanged.await();
					} finally {
						lock.unlock();
					}
				} else {
					return null;
				}
			} else {
				log.trace("one line for updated file {}", fileInstance);
				ContentLine content = new ContentLine();
				content.setFileInstance(fileInstance);
				content.setLine(line);
				return content;
			}
		}
	}

	@Override
	public ContentLine poll(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException {
		long nanosTimeout = unit.toNanos(timeout);
		while (nanosTimeout > 0) {
			checkClosed();
			long beforeNanos = System.nanoTime();
			byte[] line = readLine();
			nanosTimeout -= System.nanoTime() - beforeNanos;
			if (line == null) {
				if (monitorable && !stopRead) {
					lock.lock();
					try {
						nanosTimeout = noChanged.awaitNanos(nanosTimeout);
					} finally {
						lock.unlock();
					}
				} else {
					throw new EOFException("end of stream on file "
							+ fileInstance.getName());
				}
			} else {
				ContentLine content = new ContentLine();
				content.setFileInstance(fileInstance);
				content.setLine(line);
				return content;
			}
		}
		// timeout
		return null;
	}

	@Override
	public int drainTo(Collection<? super ContentLine> list, int maxSize)
			throws IOException {
		checkClosed();
		int totalLength = 0;
		while (maxSize > 0) {
			byte[] line = readLine();
			if (line == null) {
				if (monitorable || totalLength > 0) {
					break;
				} else {
					return -1;
				}
			} else {
				ContentLine content = new ContentLine();
				content.setFileInstance(fileInstance);
				content.setLine(line);
				boolean addSuccess = false;
				try {
					list.add(content);
					addSuccess = true;
				} finally {
					if (!addSuccess) {
						reader.pushBackLine(line);
					}
				}
				maxSize--;
				totalLength++;
			}
		}
		return totalLength;
	}

	private byte[] readLine() throws IOException {
		if (stopRead) {
			log.trace("no data for stop reading file {}", fileInstance);
			if (sourceObserver != null) {
				sourceObserver.sourceFileDeleted(this.fileInstance,
						this.fileInstance);
				// make sure only one event
				sourceObserver = null;
			}
			return null;
		} else {
			log.trace("fetch one line from file {}", fileInstance);
			return reader.readLine();
		}
	}

	private void checkClosed() throws IOException {
		if (closed) {
			throw new IOException("stream closed");
		}
	}

	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			reader.close();
			fileInstance.getStatistics().ioReaderCount().decrement();
			fileInstance.removeFileInstanceChangeListener(this);
			closeMonitor();
		}
	}

	private void closeMonitor() {
		if (monitorable) {
			log.debug("close file monitor on {}", fileInstance);
			fileInstance.removeFileContentChangeListener(this);
			lock.lock();
			try {
				noChanged.signalAll();
			} finally {
				lock.unlock();
			}
		}
	}

	@Override
	public void onContentChanged(FileInstance instance) {
		if (instance != fileInstance) {
			return;
		}
		lock.lock();
		try {
			noChanged.signalAll();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void onFileInstanceCreated(FileInstance instance,
			FileChangeOption option) {
		// no-op;
	}

	@Override
	public void onFileInstanceDeleted(FileInstance instance,
			FileChangeOption option) {
		if (option.isRenameOption()) {
			// record previous offset
			saveReadOffset(option.getRenameFile(), reader.position());
		}
		if (!stopRead) {
			stopRead = true;
			log.debug("stop read on {} file {}",
					option.isRenameOption() ? "rename" : "deleted",
					fileInstance);
			// force end of file
			closeMonitor();
		}
	}

	static void saveReadOffset(FileInstance instance, Long offset) {
		log.debug("save read offset {} on file {}", offset, instance);
		instance.putClientProperty(OffsetTracker, offset);
	}

	static Long loadReadOffset(FileInstance instance) {
		Long offset = (Long) instance.getClientProperty(OffsetTracker);
		log.debug("load read offset {} from file {}", offset, instance);
		instance.putClientProperty(OffsetTracker, null);
		return offset;
	}

	@Override
	public void setSourceObserver(ContentLineSourceObserver sourceObserver) {
		this.sourceObserver = sourceObserver;
	}

}
