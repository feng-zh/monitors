package com.hp.it.perf.monitor.filemonitor;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ContentUpdateObserver implements Observer {

	private BlockingQueue<Integer> updatedQueue = new LinkedBlockingQueue<Integer>();

	private ConcurrentHashMap<Integer, FileContentProvider> checker = new ConcurrentHashMap<Integer, FileContentProvider>();
	
	@Override
	public void update(Observable o, Object arg) {
		ContentUpdateObservable updater = (ContentUpdateObservable) o;
		FileContentProvider provider = updater.getProvider();
		long tickCount = (Long) arg;
		if (checker.putIfAbsent(updater.getIndex(), provider) == null) {
			updatedQueue.offer(updater.getIndex());
		}
	}

	public FileContentProvider take() throws InterruptedException {
		Integer providerIndex = updatedQueue.take();
		return checker.remove(providerIndex);
	}

	public FileContentProvider poll(long timeout, TimeUnit unit)
			throws InterruptedException {
		Integer providerIndex = updatedQueue.poll(timeout, unit);
		if (providerIndex == null) {
			return null;
		} else {
			return checker.remove(providerIndex);
		}
	}

	public FileContentProvider poll() {
		Integer providerIndex = updatedQueue.poll();
		if (providerIndex == null) {
			return null;
		} else {
			return checker.remove(providerIndex);
		}
	}

}
