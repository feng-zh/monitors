package com.hp.it.perf.monitor.filemonitor;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompositeContentProvider implements FileContentProvider {

	private static final Logger log = LoggerFactory
			.getLogger(CompositeContentProvider.class);

	private List<FileContentProvider> providers = new ArrayList<FileContentProvider>();

	private Queue<FileContentProvider> lastUpdates = new LinkedList<FileContentProvider>();

	protected ContentUpdateObservable updateObservable = new ContentUpdateObservable(
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
		while (maxSize > 0) {
			if (updated == null) {
				updated = lastUpdates.poll();
			}
			if (updated == null) {
				updated = updateNotifier.poll();
				if (updated == this) {
					// just notified for check again
					log.trace("notify by change to refetch");
					continue;
				}
			}
			if (updated == null) {
				break;
			}
			// reset version
			// TODO concurrent issue
			int len = updated.readLines(list, maxSize);
			if (len == -1) {
				// TODO EOF of file
			} else if (len > 0) {
				totalLen += len;
				maxSize -= len;
				if (maxSize <= 0) {
					// still not finished
					lastUpdates.offer(updated);
				}
			} else {
				// no data loaded
			}
		}
		return totalLen;
	}

	@Override
	public void init() throws IOException {
		for (FileContentProvider provider : providers) {
			provider.addUpdateObserver(updateNotifier);
			provider.init();
		}
	}

	@Override
	public void close() throws IOException {
		for (FileContentProvider provider : providers) {
			provider.removeUpdateObserver(updateNotifier);
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
		updateObservable.addObserver(observer);
	}

	@Override
	public void removeUpdateObserver(Observer observer) {
		updateObservable.deleteObserver(observer);
	}

}
