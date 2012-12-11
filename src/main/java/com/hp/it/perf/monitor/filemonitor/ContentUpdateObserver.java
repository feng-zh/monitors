package com.hp.it.perf.monitor.filemonitor;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ContentUpdateObserver<T extends FileContentProvider> implements
		Observer {

	private BlockingQueue<Integer> updatedQueue = new LinkedBlockingQueue<Integer>();

	private ConcurrentHashMap<Integer, T> checker = new ConcurrentHashMap<Integer, T>();

	private Class<T> providerClass;

	public ContentUpdateObserver(Class<T> providerClass) {
		this.providerClass = providerClass;
	}

	@Override
	public void update(Observable o, Object arg) {
		ContentUpdateObservable<?> updater = (ContentUpdateObservable<?>) o;
		T provider = providerClass.cast(updater.getProvider());
		long tickCount = (Long) arg;
		if (checker.putIfAbsent(updater.getIndex(), provider) == null) {
			updatedQueue.offer(updater.getIndex());
		}
	}

	public T take() throws InterruptedException {
		Integer providerIndex = updatedQueue.take();
		return checker.remove(providerIndex);
	}

	public T poll(long timeout, TimeUnit unit) throws InterruptedException {
		Integer providerIndex = updatedQueue.poll(timeout, unit);
		if (providerIndex == null) {
			return null;
		} else {
			return checker.remove(providerIndex);
		}
	}

	public T poll() {
		Integer providerIndex = updatedQueue.poll();
		if (providerIndex == null) {
			return null;
		} else {
			return checker.remove(providerIndex);
		}
	}

}
