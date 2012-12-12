package com.hp.it.perf.monitor.filemonitor;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class CompositeContentProvider implements FileContentProvider {

	private List<FileContentProvider> providers = new ArrayList<FileContentProvider>();

	private ContentUpdateObservable updateObservable = new ContentUpdateObservable(
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
			FileContentProvider updated = updateNotifier.take();
			int len = updated.readLines(container, 1);
			if (len == 1) {
				return container.poll();
			} else if (len == -1) {
				// TODO EOF of file
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
			FileContentProvider updated = updateNotifier.poll(nanoTimeout,
					TimeUnit.NANOSECONDS);
			nanoTimeout = totalNanoTimeout
					- (System.nanoTime() - startNanoTime);
			if (nanoTimeout <= 0 || updated == null) {
				break;
			}
			int len = updated.readLines(container, 1);
			if (len == 1) {
				return container.poll();
			} else if (len == -1) {
				// TODO EOF of file
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
		FileContentProvider updated;
		while (maxSize > 0 && (updated = updateNotifier.poll()) != null) {
			// reset version
			int len = updated.readLines(list, maxSize);
			if (len == -1) {
				// TODO EOF of file
			} else {
				totalLen += len;
				maxSize -= len;
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
	public List<FileContentInfo> getFileContentInfos() throws IOException {
		// TODO Auto-generated method stub
		return null;
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
