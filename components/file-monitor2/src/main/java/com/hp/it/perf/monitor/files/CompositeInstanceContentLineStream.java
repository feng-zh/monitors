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

public class CompositeInstanceContentLineStream implements ContentLineStream,
		FileInstanceChangeListener {

	private Map<Integer, FileInstance> allInstances = new HashMap<Integer, FileInstance>();

	private int instanceSequence = 0;

	private final Object instanceTracker = new Object();

	private final Object streamTracker = new Object();

	private final FileContentChangeQueue<FileInstance> fileUpdateNotifier = new FileContentChangeQueue<FileInstance>();

	private Deque<FileInstance> lastUpdateFiles = new LinkedList<FileInstance>();

	private ContentLineStreamProviderDelegator streamDelegator;

	private FileOpenOption openOption;

	private volatile boolean closed;

	private String name;

	private static final Logger log = LoggerFactory
			.getLogger(CompositeInstanceContentLineStream.class);

	public CompositeInstanceContentLineStream(String name,
			FileOpenOption openOption,
			ContentLineStreamProviderDelegator streamDelegator) {
		this.name = name;
		this.openOption = openOption;
		this.streamDelegator = streamDelegator;
	}

	public void addFileInstance(FileInstance instance) throws IOException {
		addInstance(instance);
		if (openOption.openOnTail()) {
			getContentStream(instance);
		}
	}

	protected void addInstance(FileInstance instance) {
		Integer seq = (Integer) instance.getClientProperty(instanceTracker);
		if (seq == null) {
			log.debug("add file instance into streams: {}", instance);
			instance.putClientProperty(instanceTracker, ++instanceSequence);
			allInstances.put(instanceSequence, instance);
			if (openOption.openOnTail()) {
				log.debug("add file into last updated queue: {}", instance);
				lastUpdateFiles.offer(instance);
				fileUpdateNotifier.notifyCheck();
			}
		}
	}

	protected void removeInstance(FileInstance instance) {
		Integer seq = (Integer) instance.getClientProperty(instanceTracker);
		if (seq != null) {
			log.debug("remove file instance from streams: {}", instance);
			allInstances.remove(seq);
			instance.putClientProperty(instanceTracker, null);
		}
	}

	@Override
	public ContentLine poll() throws IOException {
		checkClosed();
		ContentLine content = null;
		FileInstance file = null;
		while (content == null) {
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

	@Override
	public int drainTo(Collection<? super ContentLine> list, int maxSize)
			throws IOException {
		checkClosed();
		int totalLen = 0;
		FileInstance file = null;
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

	private ContentLineStream getContentStream(FileInstance file)
			throws IOException {
		ContentLineStream stream = (ContentLineStream) file
				.getClientProperty(streamTracker);
		if (stream == null) {
			stream = streamDelegator.openLineStream(file, openOption);
			file.putClientProperty(streamTracker, stream);
			fileUpdateNotifier.addFileContentChangeAware(file);
		}
		return stream;
	}

	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			for (FileInstance file : allInstances.values()) {
				ContentLineStream stream = (ContentLineStream) file
						.getClientProperty(streamTracker);
				if (stream != null) {
					fileUpdateNotifier.removeFileContentChangeAware(file);
				}
			}
			onClosing();
			close(fileUpdateNotifier);
			for (FileInstance file : allInstances.values()) {
				ContentLineStream stream = (ContentLineStream) file
						.getClientProperty(streamTracker);
				if (stream != null) {
					file.putClientProperty(streamTracker, null);
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

	@Override
	public ContentLine take() throws IOException, InterruptedException {
		checkClosed();
		while (true) {
			FileInstance file = lastUpdateFiles.poll();
			if (file == null) {
				log.trace("start take updated file from {}", name);
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
			FileInstance file = lastUpdateFiles.poll();
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

	protected void checkClosed() throws IOException {
		if (closed) {
			throw new IOException("closed stream");
		}
	}

	@Override
	public void onFileInstanceCreated(FileInstance instance,
			FileChangeOption changeOption) {
		addInstance(instance);
		if (changeOption.isRenameOption()) {
			notifyIfEmpty();
		}
	}

	@Override
	public void onFileInstanceDeleted(FileInstance instance,
			FileChangeOption changeOption) {
		removeInstance(instance);
		if (!changeOption.isRenameOption()) {
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

}
