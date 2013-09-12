package com.hp.it.perf.monitor.files;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.FileInstanceChangeListener.FileChangeOption;

public class SuperSetContentLineStream implements ContentLineStream {

	private FileOpenOption openOption;

	private volatile boolean closed;

	private static final Logger log = LoggerFactory
			.getLogger(SuperSetContentLineStream.class);

	private Map<ContentLineStreamProvider, ContentLineStream> allInstances = new HashMap<ContentLineStreamProvider, ContentLineStream>();

	private Map<ContentLineStreamProvider, ChangeListener> changeListeners = new HashMap<ContentLineStreamProvider, ChangeListener>();

	private final FileContentChangeQueue<ContentLineStreamProvider> fileUpdateNotifier = new FileContentChangeQueue<ContentLineStreamProvider>();

	private Deque<ContentLineStreamProvider> lastUpdateFiles = new LinkedList<ContentLineStreamProvider>();

	private class ChangeListener implements FileInstanceChangeListener {

		private ContentLineStreamProvider provider;

		ChangeListener(ContentLineStreamProvider provider) {
			this.provider = provider;
		}

		@Override
		public void onFileInstanceCreated(FileInstance instance,
				FileChangeOption changeOption) {
			fireFileInstanceCreated(provider, instance, changeOption);
		}

		@Override
		public void onFileInstanceDeleted(FileInstance instance,
				FileChangeOption changeOption) {
			fireFileInstanceDeleted(provider, instance, changeOption);
		}

	}

	public SuperSetContentLineStream(FileOpenOption openOption) {
		this.openOption = openOption;
	}

	private void fireFileInstanceDeleted(ContentLineStreamProvider provider,
			FileInstance instance, FileChangeOption changeOption) {
		if (!changeOption.isRenameOption()) {
			notifyIfEmpty();
		}
	}

	private void fireFileInstanceCreated(ContentLineStreamProvider provider,
			FileInstance instance, FileChangeOption changeOption) {
		if (openOption.openOnTail()) {
			log.debug("add file into last updated queue due to new file: {}",
					instance);
			lastUpdateFiles.offer(provider);
			fileUpdateNotifier.notifyCheck();
		}
		if (changeOption.isRenameOption()) {
			notifyIfEmpty();
		}
	}

	private void notifyIfEmpty() {
		if (allInstances.isEmpty()) {
			fileUpdateNotifier.notifyEmpty();
		} else {
			fileUpdateNotifier.clearEmpty();
		}
	}

	public void addFileSet(FileSet fileSet) throws IOException {
		addProvider((ContentLineStreamProvider) fileSet);
	}

	private void addProvider(ContentLineStreamProvider provider)
			throws IOException {
		allInstances.put(provider, null);
		if (provider instanceof FileInstanceChangeAware) {
			ChangeListener listener = new ChangeListener(provider);
			((FileInstanceChangeAware) provider)
					.addFileInstanceChangeListener(listener);
			changeListeners.put(provider, listener);
		}
		if (openOption.openOnTail()) {
			getContentStream(provider);
			lastUpdateFiles.offerFirst(provider);
		}
	}

	public void addFileInstance(FileInstance instance) throws IOException {
		addProvider((ContentLineStreamProvider) instance);
	}

	public void removeFileInstance(FileInstance instance) throws IOException {
		removeProvider((ContentLineStreamProvider) instance);
	}

	private void removeProvider(ContentLineStreamProvider provider)
			throws IOException {
		if (provider instanceof FileInstanceChangeAware) {
			ChangeListener listener = changeListeners.remove(provider);
			if (listener != null) {
				((FileInstanceChangeAware) provider)
						.removeFileInstanceChangeListener(listener);
			}
		}
		ContentLineStream stream = allInstances.remove(provider);
		if (stream != null) {
			stream.close();
		}
	}

	public void removeFileSet(FileSet fileSet) throws IOException {
		removeProvider((ContentLineStreamProvider) fileSet);
	}

	@Override
	public ContentLine poll() throws IOException {
		checkClosed();
		ContentLine content = null;
		ContentLineStreamProvider file = null;
		while (content != null) {
			if (file == null) {
				file = lastUpdateFiles.poll();
			}
			if (file == null) {
				file = fileUpdateNotifier.poll();
			}
			if (file == null) {
				break;
			}
			ContentLineStream stream = getContentStream(file);
			content = stream.poll();
			if (content != null) {
				// still not finished
				lastUpdateFiles.offerFirst(file);
			} else {
				// no data loaded
				file = null;
			}
		}
		return content;
	}

	private ContentLineStream getContentStream(ContentLineStreamProvider file)
			throws IOException {
		ContentLineStream stream = allInstances.get(file);
		if (stream == null) {
			stream = file.open(openOption);
			allInstances.put(file, stream);
			fileUpdateNotifier.addFileContentChangeAware(file);
		}
		return stream;
	}

	@Override
	public ContentLine take() throws IOException, InterruptedException {
		checkClosed();
		while (true) {
			ContentLineStreamProvider file = lastUpdateFiles.poll();
			if (file == null) {
				log.trace("start take updated file");
				try {
					file = fileUpdateNotifier.take();
					if (file == null) {
						continue;
					}
				} catch (EOFException e) {
					return null;
				}
				log.trace("fetch one line for updated file {}", file);
			}
			ContentLineStream stream = getContentStream(file);
			ContentLine content = stream.poll();
			if (content != null) {
				log.trace("read 1 line from {}", file);
				lastUpdateFiles.offerFirst(file);
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
			ContentLineStreamProvider file = lastUpdateFiles.poll();
			if (file == null) {
				file = fileUpdateNotifier.poll(nanoTimeout,
						TimeUnit.NANOSECONDS);
				nanoTimeout = totalNanoTimeout
						- (System.nanoTime() - startNanoTime);
				if (nanoTimeout <= 0) {
					break;
				}
				if (file == null) {
					continue;
				}
			}
			ContentLineStream stream = getContentStream(file);
			ContentLine content = stream.poll();
			if (content != null) {
				lastUpdateFiles.offerFirst(file);
				return content;
			} else {
				// no data loaded
			}
			nanoTimeout = totalNanoTimeout
					- (System.nanoTime() - startNanoTime);
		}
		// timeout
		return null;
	}

	@Override
	public int drainTo(Collection<? super ContentLine> list, int maxSize)
			throws IOException {
		checkClosed();
		int totalLen = 0;
		ContentLineStreamProvider file = null;
		while (maxSize > 0) {
			if (file == null) {
				file = lastUpdateFiles.poll();
			}
			if (file == null) {
				file = fileUpdateNotifier.poll();
			}
			if (file == null) {
				break;
			}
			ContentLineStream stream = getContentStream(file);
			// TODO queue full
			int len = stream.drainTo(list, maxSize);
			if (len > 0) {
				totalLen += len;
				maxSize -= len;
				if (maxSize <= 0) {
					// still not finished
					lastUpdateFiles.offerFirst(file);
				}
			} else {
				// no data loaded
				file = null;
			}
		}
		return totalLen;
	}

	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			for (ContentLineStreamProvider file : allInstances.keySet()) {
				ContentLineStream stream = allInstances.get(file);
				if (stream != null) {
					fileUpdateNotifier.removeFileContentChangeAware(file);
				}
			}
			onClosing();
			close(fileUpdateNotifier);
			for (ContentLineStreamProvider file : allInstances.keySet()) {
				ContentLineStream stream = allInstances.get(file);
				if (stream != null) {
					close(stream);
				}
			}
			onClosed();
		}
	}

	protected void onClosing() {
		// for-extends
	}

	protected void onClosed() {
		// for-extends
	}

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
