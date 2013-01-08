package com.hp.it.perf.monitor.filemonitor;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompositeContentProvider extends ManagedFileContentProvider
		implements FileContentProvider, CompositeContentProviderMXBean {

	private static final Logger log = LoggerFactory
			.getLogger(CompositeContentProvider.class);

	private List<FileContentProvider> providers = new ArrayList<FileContentProvider>();

	private Queue<FileContentProvider> lastUpdates = new LinkedList<FileContentProvider>();

	protected ContentUpdateObservable externalUpdater = new ContentUpdateObservable(
			this);

	private ContentUpdateObserver updateNotifier = new ContentUpdateObserver();

	public List<FileContentProvider> getProviders() {
		return providers;
	}

	public void setProviders(List<FileContentProvider> providers) {
		this.providers = providers;
	}

	@Override
	public LineRecord readLine() throws IOException, InterruptedException {
		BlockingQueue<LineRecord> container = new ArrayBlockingQueue<LineRecord>(
				1);
		while (true) {
			FileContentProvider updated = lastUpdates.poll();
			if (updated == null) {
				log.trace("start taking updated provider on {}", this);
				updated = updateNotifier.take();
				if (updated == this) {
					updated = null;
					// just notified for check again
					log.trace("notify by change to refetch");
					continue;
				}
				log.trace("fetch one line for updated content provider {}",
						updated);
			}
			// TODO concurrent issue
			int len = updated.readLines(container, 1);
			log.trace("read line count {}", len);
			if (len == 1) {
				LineRecord line = container.poll();
				lastUpdates.offer(updated);
				onLineRead(line);
				return line;
			} else if (len == -1) {
				// TODO EOF of file
			} else {
				// no data loaded
			}
		}
	}

	@Override
	public LineRecord readLine(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException {
		long startNanoTime = System.nanoTime();
		long totalNanoTimeout = unit.toNanos(timeout);
		long nanoTimeout = totalNanoTimeout;
		BlockingQueue<LineRecord> container = new ArrayBlockingQueue<LineRecord>(
				1);
		while (nanoTimeout > 0) {
			FileContentProvider updated = lastUpdates.poll();
			if (updated == null) {
				updated = updateNotifier
						.poll(nanoTimeout, TimeUnit.NANOSECONDS);
				nanoTimeout = totalNanoTimeout
						- (System.nanoTime() - startNanoTime);
				if (nanoTimeout <= 0 || updated == null) {
					break;
				}
				if (updated == this) {
					updated = null;
					// just notified for check again
					log.trace("notify by change to refetch");
					continue;
				}
			}
			// TODO concurrent issue
			int len = updated.readLines(container, 1);
			if (len == 1) {
				LineRecord line = container.poll();
				lastUpdates.offer(updated);
				onLineRead(line);
				return line;
			} else if (len == -1) {
				// TODO EOF of file
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
	public int readLines(Queue<LineRecord> list, int maxSize)
			throws IOException {
		int totalLen = 0;
		FileContentProvider updated = null;
		RecordedQueue<LineRecord> recordedQueue = new RecordedQueue<LineRecord>(
				list);
		while (maxSize > 0) {
			if (updated == null) {
				updated = lastUpdates.poll();
			}
			if (updated == null) {
				updated = updateNotifier.poll();
				if (updated == this) {
					updated = null;
					// just notified for check again
					log.trace("notify by change to refetch");
					continue;
				}
			}
			if (updated == null) {
				log.trace("no updated");
				break;
			}
			// reset version
			// TODO concurrent issue
			int len = updated.readLines(list, maxSize);
			if (len == EOF) {
				// TODO EOF of file
				log.debug("file is reaching EOF: {}", updated);
				updated = null;
			} else if (len == QUEUE_FULL) {
				// queue is full
				// file not loaded finished
				lastUpdates.offer(updated);
				for (LineRecord line : recordedQueue.getRecorded()) {
					onLineRead(line);
				}
				return QUEUE_FULL;
			} else if (len > 0) {
				totalLen += len;
				maxSize -= len;
				if (maxSize <= 0) {
					// still not finished
					lastUpdates.offer(updated);
				}
			} else {
				// no data loaded
				updated = null;
			}
		}
		for (LineRecord line : recordedQueue.getRecorded()) {
			onLineRead(line);
		}
		return totalLen;
	}

	@Override
	public void init() throws IOException {
		for (FileContentProvider provider : providers) {
			provider.addUpdateObserver(updateNotifier);
			provider.addUpdateObserver(externalUpdater);
			provider.init();
		}
	}

	@Override
	public void close() throws IOException {
		for (FileContentProvider provider : providers) {
			provider.removeUpdateObserver(updateNotifier);
			provider.removeUpdateObserver(externalUpdater);
			provider.close();
		}
	}

	@Override
	public List<FileContentInfo> getFileContentInfos(boolean realtime)
			throws IOException {
		List<FileContentInfo> infos = new ArrayList<FileContentInfo>(
				providers.size());
		for (FileContentProvider f : providers) {
			infos.addAll(f.getFileContentInfos(realtime));
		}
		return infos;
	}

	@Override
	public void addUpdateObserver(Observer observer) {
		externalUpdater.addObserver(observer);
	}

	@Override
	public void removeUpdateObserver(Observer observer) {
		externalUpdater.deleteObserver(observer);
	}

	@Override
	public String toString() {
		return String.format("CompositeContentProvider %s", providers);
	}

	@Override
	public String[] getFileNames() {
		List<String> fileList = new ArrayList<String>();
		for (FileContentProvider file : providers) {
			if (file instanceof UniqueFile) {
				fileList.add(((UniqueFile) file).getFileName());
			} else if (file instanceof FolderContentProvider) {
				fileList.add(((FolderContentProvider) file).getFolderName());
			} else if (file instanceof CompositeContentProvider) {
				fileList.addAll(Arrays.asList(((CompositeContentProvider) file)
						.getFileNames()));
			}
		}
		return fileList.toArray(new String[fileList.size()]);
	}

	@Override
	protected String getProviderName() {
		return "Composite:" + Arrays.toString(getFileNames());
	}

	@Override
	protected Collection<FileContentProvider> providers() {
		return providers;
	}

}
