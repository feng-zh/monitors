package com.hp.it.perf.monitor.files;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FileContentChangeQueue<T> implements Closeable {

	// accessed by different threads
	private BlockingQueue<Object> updatedQueue = new LinkedBlockingQueue<Object>();

	// accessed by different threads
	private ConcurrentHashMap<Object, T> checker = new ConcurrentHashMap<Object, T>();

	private ConcurrentHashMap<T, ContentChangeListener> listeners = new ConcurrentHashMap<T, ContentChangeListener>();

	private volatile boolean closed;

	private final Object EMPTY = new Object();

	private final Object CHECK = new Object();

	private class ContentChangeListener implements FileContentChangeListener {

		private T provider;

		ContentChangeListener(T provider) {
			this.provider = provider;
		}

		@Override
		public void onContentChanged(FileInstance instance) {
			fireContentChanged(provider, instance);
		}

	}

	private void fireContentChanged(T provider, FileInstance instance) {
		if (closed) {
			// ignore if closed
			// TODO log to detect listener not unregister
			return;
		}
		Object index = provider;
		if (index == null) {
			// ignore
			return;
		}
		if (checker.putIfAbsent(index, provider) == null) {
			updatedQueue.offer(index);
		}
	}

	public T take() throws InterruptedException, IOException, EOFException {
		if (closed) {
			throw new IOException("closed change queue");
		}
		Object index = updatedQueue.take();
		if (index == this) {
			updatedQueue.offer(index);
			throw new IOException("closed change queue");
		} else if (index == CHECK) {
			return null;
		} else if (index == EMPTY) {
			updatedQueue.offer(index);
			throw new EOFException("no more instance");
		} else {
			return checker.remove(index);
		}
	}

	public T poll(long timeout, TimeUnit unit) throws InterruptedException,
			IOException, EOFException {
		if (closed) {
			throw new IOException("closed change queue");
		}
		Object index = updatedQueue.poll(timeout, unit);
		if (index == null) {
			return null;
		} else if (index == CHECK) {
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

	public T poll() throws IOException {
		if (closed) {
			throw new IOException("closed change queue");
		}
		Object index = updatedQueue.poll();
		if (index == null) {
			return null;
		} else if (index == CHECK) {
			return poll();
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

	public void notifyCheck() {
		if (updatedQueue.isEmpty()) {
			updatedQueue.offer(CHECK);
		}
	}

	public void addFileContentChangeAware(T provider) {
		removeFileContentChangeAware(provider);
		ContentChangeListener listener = new ContentChangeListener(provider);
		((ContentLineStreamProvider) provider)
				.addFileContentChangeListener(listener);
		listeners.put(provider, listener);
	}

	public void removeFileContentChangeAware(T provider) {
		if (!(provider instanceof ContentLineStreamProvider)) {
			throw new ClassCastException();
		}
		ContentChangeListener oldListener = listeners.remove(provider);
		if (oldListener != null) {
			((ContentLineStreamProvider) provider)
					.removeFileContentChangeListener(oldListener);
		}
	}

}
