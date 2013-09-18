package com.hp.it.perf.monitor.files;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.FileChangeQueue.ChangeEvent;

abstract class AbstractContentLineStream<T> implements ContentLineStream {

	protected FileChangeQueue<T> fileUpdateNotifier;

	private ContentLineSourceObserver sourceObserver;

	private static final Logger log = LoggerFactory
			.getLogger(AbstractContentLineStream.class);

	private volatile boolean closed;

	public void setSourceObserver(ContentLineSourceObserver sourceObserver) {
		this.sourceObserver = sourceObserver;
	}

	@Override
	public ContentLine poll() throws IOException {
		checkClosed();
		ContentLine content = null;
		while (content == null) {
			ChangeEvent<T> event = fileUpdateNotifier.poll(false);
			if (event == null) {
				// break poll
				break;
			}
			if (handleNonContentEvent(event)) {
				continue;
			}
			ContentLineStream stream = getContentStream(event);
			content = stream.poll();
			if (content != null) {
				// still not finished
				fileUpdateNotifier.pushBack(event);
			}
		}
		return content;
	}

	// true: non-change
	private boolean handleNonContentEvent(ChangeEvent<?> event) {
		if (event.getMode() != null) {
			switch (event.getMode()) {
			case Changed:
				return false;
			case Created:
				if (sourceObserver != null) {
					sourceObserver.sourceFileCreated(event.getInstance(),
							event.getProvider());
				}
				break;
			case Deleted:
				if (sourceObserver != null) {
					sourceObserver.sourceFileDeleted(event.getInstance(),
							event.getProvider());
				}
				break;
			default:
				break;
			}
		}
		return true;
	}

	@Override
	public int drainTo(Collection<? super ContentLine> list, int maxSize)
			throws IOException {
		checkClosed();
		int totalLen = 0;
		boolean eof = false;
		while (maxSize > 0) {
			log.trace("start poll updated file from {}", this);
			ChangeEvent<T> event;
			try {
				event = fileUpdateNotifier.poll(true);
			} catch (EOFException e) {
				eof = true;
				break;
			}
			if (event == null) {
				// break poll
				break;
			}
			if (handleNonContentEvent(event)) {
				continue;
			}
			FileInstance file = event.getInstance();
			log.trace("fetch one line for updated file {}", file);
			ContentLineStream stream = getContentStream(event);
			// TODO queue full
			int len = stream.drainTo(list, maxSize);
			if (len > 0) {
				totalLen += len;
				maxSize -= len;
				if (maxSize <= 0) {
					// still not finished
					log.trace("read {} line from {}", len, file);
					fileUpdateNotifier.pushBack(event);
				}
			} else if (len == 0) {
				// no data loaded
				log.trace("no data loaded from updated file {}", file);
			} else {
				log.trace("EOF from updated file {}", file);
			}
		}
		if (totalLen == 0 && eof) {
			return -1;
		} else {
			return totalLen;
		}
	}

	@Override
	public ContentLine take() throws IOException, InterruptedException {
		checkClosed();
		while (true) {
			ChangeEvent<T> event;
			log.trace("start take updated file from {}", this);
			try {
				event = fileUpdateNotifier.take();
			} catch (EOFException e) {
				return null;
			}
			if (handleNonContentEvent(event)) {
				continue;
			}
			FileInstance file = event.getInstance();
			log.trace("fetch one line for updated file {}", file);
			ContentLineStream stream = getContentStream(event);
			ContentLine content = stream.poll();
			if (content != null) {
				// still not finished
				log.trace("read 1 line from {}", file);
				fileUpdateNotifier.pushBack(event);
				return content;
			} else {
				// no data loaded
				log.trace("no data loaded from updated file {}", file);
			}
		}
	}

	@Override
	public ContentLine poll(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException {
		checkClosed();
		long startNanoTime = System.nanoTime();
		long totalNanoTimeout = unit.toNanos(timeout);
		long nanoTimeout = totalNanoTimeout;
		while (nanoTimeout > 0) {
			log.trace("start poll updated file from {}", this);
			ChangeEvent<T> event = fileUpdateNotifier.poll(nanoTimeout,
					TimeUnit.NANOSECONDS);
			nanoTimeout = totalNanoTimeout
					- (System.nanoTime() - startNanoTime);
			if (event == null) {
				continue;
			}
			if (handleNonContentEvent(event)) {
				continue;
			}
			FileInstance file = event.getInstance();
			log.trace("fetch one line for updated file {}", file);
			ContentLineStream stream = getContentStream(event);
			ContentLine content = stream.poll();
			if (content != null) {
				// still not finished
				log.trace("read 1 line from {}", file);
				fileUpdateNotifier.pushBack(event);
				return content;
			} else {
				// no data loaded
				log.trace("no data loaded from updated file {}", file);
			}
			nanoTimeout = totalNanoTimeout
					- (System.nanoTime() - startNanoTime);
		}
		// timeout
		return null;
	}

	protected abstract ContentLineStream getContentStream(ChangeEvent<T> event)
			throws IOException;

	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			onClosing();
			close(fileUpdateNotifier);
			onClosed();
		}
	}

	protected abstract void onClosing();

	protected abstract void onClosed();

	private void close(Closeable closeable) {
		try {
			closeable.close();
		} catch (IOException e) {
			// TODO log it
		}
	}

	protected void checkClosed() throws IOException {
		if (closed) {
			throw new IOException("closed stream");
		}
	}
}
