package example;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

abstract class FileMonitorKeyImpl implements FileMonitorKey, Runnable {

	private final File file;

	private volatile ScheduledFuture<?> future;

	private List<FileMonitorListener> listeners = new CopyOnWriteArrayList<FileMonitorListener>();

	private final FileMonitorMode mode;

	private volatile boolean closed;

	private File changedFile;

	private volatile SelfMonitorListener selfListener;

	private class SelfMonitorListener implements FileMonitorListener {

		private FileMonitorEvent event;
		private CountDownLatch latch = new CountDownLatch(1);

		@Override
		public void onChanged(FileMonitorEvent event) {
			if (latch.getCount() > 0) {
				this.event = event;
				latch.countDown();
			}
		}

		public FileMonitorEvent await() throws InterruptedException {
			latch.await();
			return event;
		}

		public FileMonitorEvent await(long timeout, TimeUnit unit)
				throws InterruptedException {
			if (latch.await(timeout, unit)) {
				// get event
				return event;
			} else {
				// timeout
				return null;
			}
		}
	}

	public FileMonitorKeyImpl(File file, FileMonitorMode mode) {
		this.file = file;
		this.mode = mode;
		this.selfListener = new SelfMonitorListener();
	}

	public void addMonitorListener(FileMonitorListener listener) {
		listeners.add(listener);
	}

	public void removeMonitorListener(FileMonitorListener listener) {
		listeners.remove(listener);
	}

	public void close() {
		closed = true;
		ScheduledFuture<?> lFuture = future;
		if (lFuture != null) {
			lFuture.cancel(false);
			future = null;
		}
	}

	public File getMonitorFile() {
		return file;
	}

	// processed in scheduler
	public void run() {
		try {
			if (isChanged()) {
				FileMonitorEvent event = new FileMonitorEvent(this);
				event.setChangedFile(changedFile);
				event.setMonitorFile(file);
				event.setMonitorMode(mode);
				// FIXME re-change?
				selfListener.onChanged(event);
				onChanged(event);
			}
		} catch (Exception e) {
			// TODO log error
		}
	}

	protected void onChanged(FileMonitorEvent event) {
		for (FileMonitorListener listener : listeners) {
			listener.onChanged(event);
		}
	}

	protected void setChangedFile(File changedFile) {
		this.changedFile = changedFile;
	}

	protected File getChangedFile() {
		return this.changedFile;
	}

	protected abstract boolean isChanged() throws IOException;

	void setScheduledFuture(ScheduledFuture<?> future) {
		if (closed) {
			future.cancel(false);
		} else {
			this.future = future;
		}
	}

	@Override
	public FileMonitorEvent await() throws InterruptedException {
		return selfListener.await();
	}

	@Override
	public FileMonitorEvent await(long timeout, TimeUnit unit)
			throws InterruptedException {
		return selfListener.await(timeout, unit);
	}

}
