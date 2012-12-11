package com.hp.it.perf.monitor.filemonitor;

import java.util.Observable;
import java.util.concurrent.atomic.AtomicInteger;

public class ContentUpdateObservable<T extends FileContentProvider> extends
		Observable implements FileMonitorListener {

	private final T provider;

	private static AtomicInteger sequence = new AtomicInteger();

	private int index;

	public ContentUpdateObservable(T provider) {
		this.provider = provider;
		this.index = sequence.incrementAndGet();
	}

	public T getProvider() {
		return provider;
	}

	@Override
	public void onChanged(FileMonitorEvent event) {
		notifyObservers(event.getTickCount());
	}

	int getIndex() {
		return index;
	}

}
