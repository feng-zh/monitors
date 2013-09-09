package com.hp.it.perf.monitor.files;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FileContentChangeQueue implements FileContentChangedListener,
		Closeable {

	// accessed by different threads
	private BlockingQueue<Object> updatedQueue = new LinkedBlockingQueue<Object>();

	// accessed by different threads
	private ConcurrentHashMap<Object, FileInstance> checker = new ConcurrentHashMap<Object, FileInstance>();

	private final Object instancePropertyKey;

	private volatile boolean closed;

	private final Object EMPTY = new Object();

	public FileContentChangeQueue(Object instancePropertyKey) {
		if (instancePropertyKey == null) {
			throw new IllegalArgumentException();
		}
		this.instancePropertyKey = instancePropertyKey;
	}

	@Override
	public void onContentChanged(FileInstance instance) {
		if (closed) {
			// ignore if closed
			// TODO log to detect listener not unregister
			return;
		}
		Object index = instance.getClientProperty(instancePropertyKey);
		if (index == null) {
			// ignore
			return;
		}
		if (checker.putIfAbsent(index, instance) == null) {
			updatedQueue.offer(index);
		}
	}

	public FileInstance take() throws InterruptedException, IOException,
			EOFException {
		if (closed) {
			throw new IOException("closed change queue");
		}
		Object index = updatedQueue.take();
		if (index == this) {
			updatedQueue.offer(index);
			throw new IOException("closed change queue");
		} else if (index == EMPTY) {
			updatedQueue.offer(index);
			throw new EOFException("no more instance");
		} else {
			return checker.remove(index);
		}
	}

	public FileInstance poll(long timeout, TimeUnit unit)
			throws InterruptedException, IOException, EOFException {
		if (closed) {
			throw new IOException("closed change queue");
		}
		Object index = updatedQueue.poll(timeout, unit);
		if (index == null) {
			return null;
		} else if (index == EMPTY) {
			updatedQueue.offer(index);
			throw new EOFException("no more instance");
		} else if (index == this) {
			updatedQueue.offer(index);
			throw new IOException("closed change queue");
		} else {
			return checker.remove(index);
		}
	}

	public void close() {
		if (!closed) {
			closed = true;
			// notify it is closed
			updatedQueue.offer(this);
		}
	}

	public FileInstance poll() throws IOException {
		if (closed) {
			throw new IOException("closed change queue");
		}
		Object index = updatedQueue.poll();
		if (index == null) {
			return null;
		} else if (index == EMPTY) {
			updatedQueue.offer(index);
			return null;
		} else if (index == this) {
			updatedQueue.offer(index);
			throw new IOException("closed change queue");
		} else {
			return checker.remove(index);
		}
	}

	public void notifyEmpty() {
		updatedQueue.offer(EMPTY);
	}

	public void clearEmpty() {
		if (updatedQueue.peek() == EMPTY) {
			updatedQueue.poll();
		}
	}

}
