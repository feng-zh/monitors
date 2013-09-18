package com.hp.it.perf.monitor.files;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FileChangeQueue<T> implements Closeable {

	private static final Logger log = LoggerFactory
			.getLogger(FileChangeQueue.class);

	private volatile boolean closed;

	private final Object instanceTracker = new Object();

	private AtomicInteger instanceSeqGen = new AtomicInteger();

	private BitSet instanceChangeMask = new BitSet();

	private BitSet instanceDeleteMask = new BitSet();

	private ConcurrentHashMap<T, ContentChangeListener> contentChangeListeners = new ConcurrentHashMap<T, ContentChangeListener>();

	private ConcurrentHashMap<T, InstanceChangeListener> instanceChangeListeners = new ConcurrentHashMap<T, InstanceChangeListener>();

	private List<FileInstance> monitorInstances = null;

	// accessed by different threads
	private LinkedBlockingDeque<ChangeEvent<T>> queue = new LinkedBlockingDeque<ChangeEvent<T>>();

	public enum ChangeMode {
		Created, Deleted, Changed, Empty, Closed;
	}

	public static class ChangeEvent<T> {
		private final T provider;
		private final FileInstance instance;
		private final ChangeMode mode;
		private int instanceSeq;

		public ChangeEvent(T provider, FileInstance instance, ChangeMode mode) {
			this.provider = provider;
			this.instance = instance;
			this.mode = mode;
		}

		int getInstanceSeq() {
			return instanceSeq;
		}

		void setInstanceSeq(int instanceSeq) {
			this.instanceSeq = instanceSeq;
		}

		public T getProvider() {
			return provider;
		}

		public FileInstance getInstance() {
			return instance;
		}

		public ChangeMode getMode() {
			return mode;
		}

		boolean isClosedEvent() {
			return mode == ChangeMode.Closed;
		}

		boolean isEmptyEvent() {
			return mode == ChangeMode.Empty;
		}

	}

	private class ContentChangeListener implements FileContentChangeListener {

		private T provider;

		ContentChangeListener(T provider) {
			this.provider = provider;
			if (this.provider instanceof FileContentChangeAware) {
				((FileContentChangeAware) this.provider)
						.addFileContentChangeListener(this);
			} else {
				throw new ClassCastException(provider.getClass().getName());
			}
		}

		public void remove() {
			if (this.provider instanceof FileContentChangeAware) {
				((FileContentChangeAware) this.provider)
						.removeFileContentChangeListener(this);
			}
		}

		@Override
		public void onContentChanged(FileInstance instance) {
			fireChangeEvent(new ChangeEvent<T>(provider, instance,
					ChangeMode.Changed), false);
		}

	}

	private class InstanceChangeListener implements FileInstanceChangeListener {

		private T provider;

		InstanceChangeListener(T provider) {
			this.provider = provider;
			if (this.provider instanceof FileInstanceChangeAware) {
				((FileInstanceChangeAware) this.provider)
						.addFileInstanceChangeListener(this);
			} else {
				throw new ClassCastException(provider.getClass().getName());
			}
		}

		public void remove() {
			if (this.provider instanceof FileInstanceChangeAware) {
				((FileInstanceChangeAware) this.provider)
						.removeFileInstanceChangeListener(this);
			}
		}

		@Override
		public void onFileInstanceCreated(FileInstance instance,
				FileChangeOption changeOption) {
			fireChangeEvent(new ChangeEvent<T>(provider, instance,
					ChangeMode.Created), changeOption.isRenameOption());
		}

		@Override
		public void onFileInstanceDeleted(FileInstance instance,
				FileChangeOption changeOption) {
			fireChangeEvent(new ChangeEvent<T>(provider, instance,
					ChangeMode.Deleted), changeOption.isRenameOption());
		}

	}

	public FileChangeQueue(boolean monitor) {
		if (monitor) {
			monitorInstances = new ArrayList<FileInstance>();
			ChangeEvent<T> emptyEvent = new ChangeEvent<T>(null, null,
					ChangeMode.Empty);
			offerEvent(emptyEvent, true);
		}
	}

	private void fireChangeEvent(ChangeEvent<T> event, boolean renameOption) {
		if (closed) {
			return;
		}
		prepareInstanceSeq(event);
		// handle delete/create first
		if (event.getMode() == ChangeMode.Deleted) {
			synchronized (instanceDeleteMask) {
				instanceDeleteMask.set(event.getInstanceSeq());
			}
			if (monitorInstances != null) {
				monitorInstances.remove(event.getInstance());
			}
			onFileInstanceDeleted(event.getProvider(), event.getInstance());
		} else if (event.getMode() == ChangeMode.Created) {
			if (monitorInstances != null) {
				monitorInstances.add(event.getInstance());
			}
			onFileInstanceCreated(event.getProvider(), event.getInstance());
		}
		// publish event
		offerEvent(event, true);
		// check if empty
		if (monitorInstances != null && event.getMode() == ChangeMode.Deleted
				&& !renameOption) {
			if (monitorInstances.isEmpty()) {
				ChangeEvent<T> emptyEvent = new ChangeEvent<T>(null, null,
						ChangeMode.Empty);
				offerEvent(emptyEvent, true);
			}
		}
	}

	protected void onFileInstanceDeleted(T provider, FileInstance instance) {
		// for extends
	}

	protected void onFileInstanceCreated(T provider, FileInstance instance) {
		// for extends
	}

	private boolean isMonitoredEmpty() {
		if (monitorInstances != null && monitorInstances.isEmpty()) {
			return true;
		} else {
			return false;
		}
	}

	public void addFileContentChangeAware(T provider) {
		removeFileContentChangeAware(provider);
		ContentChangeListener listener = new ContentChangeListener(provider);
		contentChangeListeners.put(provider, listener);
	}

	public void removeFileContentChangeAware(T provider) {
		if (!(provider instanceof ContentLineStreamProvider)) {
			throw new ClassCastException();
		}
		ContentChangeListener oldListener = contentChangeListeners
				.remove(provider);
		if (oldListener != null) {
			oldListener.remove();
		}
	}

	public void addFileInstanceChangeAware(T provider) {
		removeFileInstanceChangeAware(provider);
		InstanceChangeListener listener = new InstanceChangeListener(provider);
		instanceChangeListeners.put(provider, listener);
	}

	public void removeFileInstanceChangeAware(T provider) {
		if (!(provider instanceof FileInstanceChangeAware)) {
			throw new ClassCastException();
		}
		InstanceChangeListener oldListener = instanceChangeListeners
				.remove(provider);
		if (oldListener != null) {
			oldListener.remove();
		}
	}

	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			// notify it is closed
			offerEvent(new ChangeEvent<T>(null, null, ChangeMode.Closed), false);
		}
	}

	private void offerEvent(ChangeEvent<T> event, boolean tail) {
		if (event.getInstanceSeq() != 0
				&& event.getMode() == ChangeMode.Changed) {
			// check and accumulate duplicate change event
			boolean newEvent = false;
			synchronized (instanceChangeMask) {
				if (!instanceChangeMask.get(event.getInstanceSeq())) {
					instanceChangeMask.set(event.getInstanceSeq());
					newEvent = true;
				}
			}
			if (newEvent) {
				if (tail) {
					log.trace("add event {} for file {}", event.getMode(),
							event.getInstance());
					queue.offer(event);
				} else {
					log.trace("push event {} for file {}", event.getMode(),
							event.getInstance());
					queue.offerFirst(event);
				}
			}
		} else {
			if (tail) {
				log.trace("add event {} ({})", event.getMode(),
						event.getInstance());
				queue.offer(event);
			} else {
				log.trace("push event {} ({})", event.getMode(),
						event.getInstance());
				queue.offerFirst(event);
			}
		}
	}

	private void clearChangeMask(ChangeEvent<T> event) {
		if (event.getInstanceSeq() != 0
				&& event.getMode() == ChangeMode.Changed) {
			// check and accumulate duplicate change event
			synchronized (instanceChangeMask) {
				instanceChangeMask.clear(event.getInstanceSeq());
			}
		}
	}

	public ChangeEvent<T> take() throws InterruptedException, IOException,
			EOFException {
		checkClosed();
		while (true) {
			ChangeEvent<T> event = queue.take();
			if (event != null) {
				log.trace("take event {} ({})", event.getMode(),
						event.getInstance());
			}
			if (event.isClosedEvent()) {
				// close event
				offerEvent(event, false);
				throw new IOException("closed change queue");
			} else if (event.isEmptyEvent()) {
				if (isMonitoredEmpty()) {
					// real empty
					offerEvent(event, true);
					throw new EOFException("no more instance");
				} else {
					// has data now, ignore this event
					continue;
				}

			} else if (wasDeletedInstance(event)) {
				clearChangeMask(event);
				continue;
			} else {
				clearChangeMask(event);
				return event;
			}
		}
	}

	public ChangeEvent<T> poll(long timeout, TimeUnit unit)
			throws InterruptedException, IOException, EOFException {
		checkClosed();
		long startNanoTime = System.nanoTime();
		long totalNanoTimeout = unit.toNanos(timeout);
		long nanoTimeout = totalNanoTimeout;
		while (nanoTimeout > 0) {
			ChangeEvent<T> event = queue
					.poll(nanoTimeout, TimeUnit.NANOSECONDS);
			if (event != null) {
				log.trace("timed poll event {} ({})", event.getMode(),
						event.getInstance());
			}
			nanoTimeout = totalNanoTimeout
					- (System.nanoTime() - startNanoTime);
			if (event == null) {
				continue;
			} else if (event.isClosedEvent()) {
				offerEvent(event, false);
				throw new IOException("closed change queue");
			} else if (event.isEmptyEvent()) {
				if (isMonitoredEmpty()) {
					offerEvent(event, true);
					throw new EOFException("no more instance");
				} else {
					continue;
				}
			} else if (wasDeletedInstance(event)) {
				clearChangeMask(event);
				continue;
			} else {
				clearChangeMask(event);
				return event;
			}
		}
		return null;
	}

	private boolean wasDeletedInstance(ChangeEvent<T> event) {
		if (event.getMode() == ChangeMode.Changed) {
			synchronized (instanceDeleteMask) {
				if (instanceDeleteMask.get(event.getInstanceSeq())) {
					return true;
				}
			}
		}
		return false;
	}

	public ChangeEvent<T> poll(boolean errorIfEof) throws IOException,
			EOFException {
		checkClosed();
		while (true) {
			ChangeEvent<T> event = queue.poll();
			if (event != null) {
				log.trace("poll event {} ({})", event.getMode(),
						event.getInstance());
			}
			if (event == null) {
				return null;
			} else if (event.isClosedEvent()) {
				offerEvent(event, false);
				throw new IOException("closed change queue");
			} else if (event.isEmptyEvent()) {
				if (isMonitoredEmpty()) {
					offerEvent(event, true);
					if (errorIfEof) {
						throw new EOFException("no more instance");
					} else {
						return null;
					}
				} else {
					continue;
				}
			} else if (wasDeletedInstance(event)) {
				clearChangeMask(event);
				continue;
			} else {
				clearChangeMask(event);
				return event;
			}
		}
	}

	public void pushBack(ChangeEvent<T> event) {
		offerEvent(event, false);
	}

	public void addMonitorInstance(FileInstance instance) {
		log.trace("add file {} into monitored list", instance);
		monitorInstances.add(instance);
	}

	public void preCheckQueue(T provider, FileInstance instance) {
		ChangeEvent<T> event = new ChangeEvent<T>(provider, instance,
				ChangeMode.Changed);
		if (instance != null) {
			prepareInstanceSeq(event);
		}
		offerEvent(event, true);
	}

	private void prepareInstanceSeq(ChangeEvent<T> event) {
		FileInstance file = event.getInstance();
		Object instanceSeq = file.getClientProperty(instanceTracker);
		if (instanceSeq == null) {
			instanceSeq = instanceSeqGen.incrementAndGet();
			file.putClientProperty(instanceTracker, instanceSeq);
		}
		event.setInstanceSeq((Integer) instanceSeq);
	}

	private void checkClosed() throws IOException {
		if (closed) {
			throw new IOException("closed change queue");
		}
	}

	public List<FileInstance> getMonitoredInstances() {
		return monitorInstances;
	}

}
